import os,re
from fastapi import FastAPI,HTTPException
from pydantic import BaseModel
import httpx
app=FastAPI(title="AI DocMate API")
class Ask(BaseModel):
 text:str
 question:str
 language:str="auto"
 mode:str="document"
def fallback(text,q):
 words=[w.lower() for w in re.findall(r"\w+",q) if len(w)>3]
 parts=re.split(r"(?<=[.!?])\s+",text)
 ranked=sorted(parts,key=lambda s:sum(w in s.lower() for w in words),reverse=True)
 return " ".join(ranked[:6])[:5000]
async def llm(prompt):
 key=os.getenv("OPENROUTER_API_KEY")
 if not key:return None
 model=os.getenv("OPENROUTER_MODEL","openrouter/free")
 async with httpx.AsyncClient(timeout=60) as c:
  r=await c.post("https://openrouter.ai/api/v1/chat/completions",headers={"Authorization":"Bearer "+key},json={"model":model,"messages":[{"role":"user","content":prompt}]})
  if r.is_error: raise HTTPException(502,"AI provider error")
  return r.json()["choices"][0]["message"]["content"]
@app.get("/health")
def health():return {"ok":True,"ai":bool(os.getenv("OPENROUTER_API_KEY"))}
@app.post("/ask")
async def ask(x:Ask):
 if not x.text.strip():raise HTTPException(400,"Empty document")
 context=x.text[:60000]
 prompt="Answer ONLY from DOCUMENT. If absent say not found. Language: "+x.language+". Mode: "+x.mode+"\nDOCUMENT:\n"+context+"\nREQUEST:\n"+x.question
 a=await llm(prompt)
 return {"answer":a or fallback(x.text,x.question),"provider":"openrouter" if a else "local-fallback"}
