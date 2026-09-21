package com.aidocmate.app
object DocTools {
 fun extract(text:String):String {
  val dates=Regex("""\b\d{1,2}[-/.]\d{1,2}[-/.]\d{2,4}\b""").findAll(text).map{it.value}.distinct().take(20).toList()
  val money=Regex("""(?:₹|Rs\.?|INR)\s?[\d,]+(?:\.\d{1,2})?""",RegexOption.IGNORE_CASE).findAll(text).map{it.value}.distinct().take(20).toList()
  val phones=Regex("""(?<!\d)(?:\+91[- ]?)?[6-9]\d{9}(?!\d)""").findAll(text).map{it.value}.distinct().take(10).toList()
  return "Dates: "+dates.joinToString()+"\nAmounts: "+money.joinToString()+"\nPhones: "+phones.joinToString()
 }
 fun classify(t:String):String { val s=t.lowercase(); return when { "tender" in s||"emd" in s->"Tender"; "invoice" in s||"gstin" in s->"Invoice"; "resume" in s||"curriculum vitae" in s->"Resume"; "agreement" in s||"whereas" in s->"Contract"; "quotation" in s->"Quotation"; else->"General document" } }
}
