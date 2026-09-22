package com.aidocmate.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.coroutines.coroutineContext

data class AssistantJob(val id:String,val docId:String,val question:String,val language:String,val summary:Boolean,val status:String,val answer:String,val created:Long,val kind:String="ai")
class AssistantJobStore(context:Context):SQLiteOpenHelper(context.applicationContext,"assistant-jobs.db",null,2) {
    override fun onCreate(db:SQLiteDatabase) {
        db.execSQL("CREATE TABLE jobs(id TEXT PRIMARY KEY, doc_id TEXT NOT NULL, question TEXT NOT NULL, language TEXT NOT NULL, summary INTEGER NOT NULL, status TEXT NOT NULL, answer TEXT NOT NULL, created INTEGER NOT NULL, kind TEXT NOT NULL DEFAULT 'ai')")
        db.execSQL("CREATE INDEX jobs_doc ON jobs(doc_id,created)")
    }
    override fun onUpgrade(db:SQLiteDatabase,oldVersion:Int,newVersion:Int) { if(oldVersion<2) db.execSQL("ALTER TABLE jobs ADD COLUMN kind TEXT NOT NULL DEFAULT 'ai'") }
    fun create(docId:String,question:String,language:String,summary:Boolean,kind:String="ai"):String {
        val id=UUID.randomUUID().toString()
        writableDatabase.insertOrThrow("jobs",null,ContentValues().apply {
            put("id",id);put("doc_id",docId);put("question",question);put("language",language);put("summary",if(summary)1 else 0);put("status","queued");put("answer","");put("created",System.currentTimeMillis());put("kind",kind)
        });return id
    }
    private fun query(selection:String?=null,args:Array<String>?=null):List<AssistantJob> = readableDatabase.query("jobs",null,selection,args,null,null,"created DESC","200").use { c ->
        buildList { while(c.moveToNext()) {
            fun s(name:String)=c.getString(c.getColumnIndexOrThrow(name))
            add(AssistantJob(s("id"),s("doc_id"),s("question"),s("language"),c.getInt(c.getColumnIndexOrThrow("summary"))==1,s("status"),s("answer"),c.getLong(c.getColumnIndexOrThrow("created")),s("kind")))
        } }
    }
    fun list(docId:String?=null)=if(docId==null)query() else query("doc_id=?",arrayOf(docId))
    fun get(id:String)=query("id=?",arrayOf(id)).firstOrNull()
    fun set(id:String,status:String,answer:String="") { writableDatabase.update("jobs",ContentValues().apply { put("status",status);put("answer",answer) },"id=? AND status!='cancelled'",arrayOf(id)) }
    fun setDocument(id:String,docId:String) { writableDatabase.update("jobs",ContentValues().apply{put("doc_id",docId)},"id=?",arrayOf(id)) }
    fun deleteDocument(docId:String) { writableDatabase.delete("jobs","doc_id=?",arrayOf(docId)) }
}
object AssistantJobs {
    fun enqueue(context:Context,doc:SavedDoc,question:String,language:String,summary:Boolean):String {
        val id=AssistantJobStore(context).use { it.create(doc.id,question,language,summary) }
        schedule(context,id);return id
    }
    fun importDocument(context:Context,uri:android.net.Uri,language:String):String {
        context.contentResolver.takePersistableUriPermission(uri,android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val id=AssistantJobStore(context).use { it.create("",uri.toString(),language,false,"import") }
        schedule(context,id);return id
    }
    fun schedule(context:Context,id:String) {
        val job=AssistantJobStore(context).use{it.get(id)} ?: return
        val request=OneTimeWorkRequestBuilder<AssistantWorker>().setInputData(workDataOf("job" to id))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(if(job.kind=="import") NetworkType.NOT_REQUIRED else NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,15,java.util.concurrent.TimeUnit.SECONDS).addTag("docmate-ai").build()
        WorkManager.getInstance(context).enqueueUniqueWork("docmate-ai-$id",ExistingWorkPolicy.KEEP,request)
    }
    fun cancel(context:Context,id:String) {
        AssistantJobStore(context).use { it.set(id,"cancelled","Cancelled by you") }
        WorkManager.getInstance(context).cancelUniqueWork("docmate-ai-$id")
    }
    fun recover(context:Context) { AssistantJobStore(context).use { store -> store.list().filter { it.status in listOf("queued","running","retrying") }.forEach { schedule(context,it.id) } } }
}
class AssistantWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result=withContext(Dispatchers.IO) {
        val id=inputData.getString("job") ?: return@withContext Result.failure()
        AssistantJobStore(applicationContext).use { store ->
            val job=store.get(id) ?: return@withContext Result.failure()
            if(job.status in listOf("completed","cancelled")) return@withContext Result.success()
            val documents=DocumentStore(applicationContext)
            if(job.kind=="import") {
                store.set(id,"running")
                try {
                    val doc=documents.get(job.id) ?: documents.import(android.net.Uri.parse(job.question),job.language,job.id)
                    coroutineContext.ensureActive()
                    store.setDocument(id,doc.id);store.set(id,"completed","Imported ${doc.name}")
                    return@withContext Result.success()
                } catch(e:kotlinx.coroutines.CancellationException) { throw e }
                catch(e:Exception) { store.set(id,"failed",e.message ?: "Import failed");return@withContext Result.failure() }
            }
            val doc=documents.get(job.docId) ?: run { store.set(id,"failed","Document was deleted");return@withContext Result.failure() }
            store.set(id,"running")
            try {
                val prior=store.list(job.docId).filter { it.kind=="ai" && it.status=="completed" && it.created<job.created }.take(4).reversed()
                val answer=AiClient.ask(applicationContext,doc.pages,job.question,job.language,job.summary,prior)
                coroutineContext.ensureActive()
                if(store.get(id)?.status=="cancelled") return@withContext Result.success()
                if(!documents.updateResult(job.docId,answer)) { store.set(id,"failed","Document was deleted");return@withContext Result.failure() }
                store.set(id,"completed",answer);Result.success()
            } catch(e:kotlinx.coroutines.CancellationException) { throw e }
            catch(e:java.io.IOException) {
                if(runAttemptCount<2) { store.set(id,"retrying","Connection interrupted; retry scheduled");Result.retry() }
                else { store.set(id,"failed","Connection failed. Retry when online.");Result.failure() }
            } catch(e:Exception) { store.set(id,"failed",e.message ?: "AI request failed");Result.failure() }
        }
    }
}
