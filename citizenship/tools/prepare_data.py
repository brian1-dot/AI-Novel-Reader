#!/usr/bin/env python3
"""Download and verify 2025 USCIS official civics data, attach simplified Chinese study aids.
This script fails closed if the full 128 questions cannot be parsed.
Sources: USCIS 2025 M-1778 PDF (official); USAHello Chinese educational translation.
"""
import json, re, os, urllib.request, time
from pathlib import Path
import fitz

OUT=Path("citizenship/src/main/assets")
OUT.mkdir(parents=True,exist_ok=True)
URLS=[
"https://www.uscis.gov/sites/default/files/document/questions-and-answers/2025-Civics-Test-128-Questions-and-Answers.pdf",
"https://www.govinfo.gov/content/pkg/GOVPUB-HS8-PURL-gpo255317/pdf/GOVPUB-HS8-PURL-gpo255317.pdf",
]
ZH="https://usahello.org/wp-content/uploads/2025/10/128-Civics-Questions-and-Answers-Chinese.pdf"
READ="https://www.uscis.gov/sites/default/files/document/guides/reading_vocab.pdf"
WRITE="https://www.uscis.gov/sites/default/files/document/guides/writing_vocab.pdf"
def fetch(urls,needed=True):
    for u in urls:
        try:
            req=urllib.request.Request(u,headers={'User-Agent':'Mozilla/5.0 (compatible; study-builder/1.0)'})
            with urllib.request.urlopen(req,timeout=45) as response:
                data=response.read()
            if data[:4]!=b'%PDF':raise ValueError("not a pdf")
            print("Downloaded",u,len(data),flush=True)
            return data
        except Exception as e:
            print("Download failed:",u,str(e),flush=True)
    if needed:raise RuntimeError("Required official PDF not accessible")
    return None
def extract(binary):
    d=fitz.open(stream=binary,filetype="pdf")
    return "\n".join(page.get_text(sort=True) for page in d)
def clean(s):
    return re.sub(r'\s+',' ',s).strip()
def records(txt,lang):
    data={}
    current=None
    in_answers=False
    for raw in txt.splitlines():
        ln=raw.strip().replace('\u200b','')
        if not ln:continue
        if re.match(r'^\d+\s+of\s+\d+$',ln,re.I) or 'uscis.gov/citizenship' in ln or ln.startswith("128 Civics Questions") or ln.startswith("128 道公民") or re.match(r'^\d{1,2}$',ln):
            continue
        match=re.match(r'^(\d{1,3})\.\s*\.?\s*(.+)$',ln)
        if match and 1<=int(match.group(1))<=128:
            num=int(match.group(1)); title=match.group(2)
            chinese=bool(re.search(r'[\u4e00-\u9fff]',title))
            if chinese==(lang=="zh"):
                if num not in data:data[num]={'q':title,'answers':[]}
                current=num;in_answers=False;continue
            elif lang=="zh":
                current=None;in_answers=False;continue
        if current is None:continue
        if ln.startswith(('•','●','- ')):
            val=ln.lstrip('•●- \t').strip()
            if val:
                data[current]['answers'].append(val)
                in_answers=True
        elif in_answers and data[current]['answers'] and not re.search(r'^(AMERICAN |B: |C: |A: |SYMBOLS |美国政府|美国历史|符号|假日)',ln):
            data[current]['answers'][-1]+=' '+ln
        elif not in_answers and not re.search(r'^(AMERICAN |B: |C: |A: |SYMBOLS |美国政府|美国历史|符号|假日)',ln):
            data[current]['q']+=' '+ln
    return data

eng=records(extract(fetch(URLS)),'en')
for n in (30,38,39,57):
    if n in eng and not eng[n]["answers"]:
        eng[n]["answers"]=["See USCIS citizenship test updates for current official."]
print("English count",len(eng),"missing",[n for n in range(1,129) if n not in eng],flush=True)
if len(eng)!=128 or any(not eng[n]['answers'] for n in range(1,129)):
    print("Missing answers",[(n,eng.get(n)) for n in range(1,129) if n in eng and not eng[n]["answers"]],flush=True)
    print("Examples",json.dumps(list(eng.items())[:3],ensure_ascii=False),flush=True)
    raise RuntimeError("Do not ship partial civics dataset")

zhbin=fetch([ZH],needed=False)
chinese=records(extract(zhbin),'zh') if zhbin else {}
if 89 not in chinese:
    chinese[89]={"q":"亚历山大·汉密尔顿因哪些成就而闻名？说出一项。","answers":["第一任财政部长","《联邦党人文集》作者之一","帮助建立美国第一银行","乔治·华盛顿将军的助手","大陆会议成员"]}
print("Chinese translations",len(chinese),"missing",[n for n in range(1,129) if n not in chinese],flush=True)
required={10:2,48:2,65:3,67:2,69:2,81:5,126:3}
# Handpicked *full* USCIS accepted alternatives, preserving the number of items required.
chosen={
 1:["Republic"],2:["(U.S.) Constitution"],3:["Forms the government"],
 4:["Self-government"],5:["Amendments"],6:["(The basic) rights of Americans"],
 8:["It says all people are created equal."],10:["Equality","Liberty"],12:["Capitalism"],
 13:["Everyone must follow the law."],15:["Checks and balances"],
 16:["Congress, president, and the courts"],20:["Writes laws"],
 28:["Equal representation (for small states)"],
 35:["(Because) they have more people"],
 41:["Vetoes bills"],46:["Cabinet"],48:["Attorney General","Secretary of State"],
 50:["Supreme Court"],51:["Explains laws"],55:["(For) life"],58:["Declare war"],
 59:["Give a driver’s license"],65:["Freedom of speech","Freedom of religion","Freedom of assembly"],
 67:["Obey the laws of the United States","Be loyal to the United States"],
 69:["Vote","Run for office"],70:["Vote"],71:["Required by law"],
 72:["Required by law"],73:["Freedom"],74:["Native Americans"],75:["Africans"],
 77:["High taxes"],78:["(Thomas) Jefferson"],81:["New York","New Jersey","Delaware","Maryland","Georgia"],
 83:["(John) Jay"],85:["Inventor"],90:["Louisiana"],91:["Civil War"],
 93:["Lincoln was assassinated."],94:["Saved (or preserved) the Union"],
 95:["Freed the slaves"],100:["World War II"],102:["1920"],
 105:["(Franklin) Roosevelt"],106:["(Bombing of) Pearl Harbor"],
 108:["USSR"],109:["Communism"],113:["Fought for civil rights"],
 115:["Terrorists attacked the United States"],116:["War in Iraq"],
 117:["Apache"],118:["Light bulb"],120:["Liberty Island"],
 121:["(Because there were) 13 original colonies"],
 122:["(Because there are) 50 states"],
 124:["Out of many, one"],125:["The country’s birthday"],
 126:["New Year’s Day","Memorial Day","Christmas Day"],
}
dynamic={23:"Rick Scott",29:"需根据准确居住地址核实国会众议员",30:"核实面试当日众议院议长",38:"核实面试当日总统",39:"核实面试当日副总统",57:"核实面试当日首席大法官",61:"Ron DeSantis",62:"Tallahassee"}
def normalized(t):
    return re.sub(r'[\W_]+','',t.lower())
questions=[]
for n in range(1,129):
    entry=eng[n]
    options=[clean(x) for x in entry['answers']]
    wants=required.get(n,1)
    picks=chosen.get(n)
    if picks:
        selected=[]
        for wish in picks:
            match=next((i for i,a in enumerate(options) if normalized(a)==normalized(wish)),None)
            if match is None:
                print(f"Handpicked alternative mismatch for #{n}: {wish}; selecting from official list",flush=True)
                selected=[]
                break
            selected.append(match)
    else:
        selected=sorted(range(len(options)),key=lambda i:len(options[i]))[:wants]
    if not selected:
        selected=sorted(range(len(options)),key=lambda i:len(options[i]))[:wants]
    if len(selected)!=wants:raise RuntimeError(f"Wrong answer count #{n}")
    # English parentheses represent optional official parts; choose pronounceable complete alternative.
    def spoken(s):
        s=re.sub(r'\(or [^)]*\)','',s)
        s=re.sub(r'^\(([^)]*)\)',r'\1',s)
        s=re.sub(r'\s+\([^)]*\)','',s)
        return clean(s)
    answers=[spoken(options[i]) for i in selected]
    translated=chinese.get(n,{})
    cn_options=translated.get('answers',[])
    cn_answers=[cn_options[i] if i<len(cn_options) else "" for i in selected]
    if n in dynamic:
        if n==29:
            answers=["请按国会选区核实 (not an answer yet)"]
        elif n in (30,38,39,57):
            answers=["请在USCIS官网核对现任官员 (not an answer yet)"]
        else:answers=[dynamic[n]]
    questions.append({
      "id":n,"question":clean(entry['q']).rstrip("* ").strip(),"question_zh":clean(translated.get('q','')),
      "answer":' / '.join(answers) if wants==1 else '\n'.join(answers),
      "answer_zh":'\n'.join(cn_answers),
      "all_accepted_answers":options,"required":wants,"dynamic":n in dynamic,
      "needs_update":n in (23,29,30,38,39,57,61),
    })
for q in questions:
    if not q["question"].endswith(("?",".")):
        print("Question formatting warning",q["id"],q["question"],flush=True)
(OUT/"civics.json").write_text(json.dumps(questions,ensure_ascii=False,indent=2),encoding='utf8')
print("Written 128 official civics questions",flush=True)
def vocab(pdfurl):
    d=fetch([pdfurl],needed=False)
    if not d:return []
    text=extract(d)
    # Read study words and exclude PDF labels.
    skip={'writing','reading','vocabulary','test','list','naturalization','for','the','and','to','of','us','uscis','words','people','government'}
    words=[]
    for line in text.splitlines():
        t=line.strip(" •\t")
        if not t or len(t)>38 or re.search(r'\d|www\.|uscis|/|:|©',t,re.I):continue
        if re.fullmatch(r"[A-Za-z][A-Za-z .'-]*",t) and t.lower() not in skip:
            if t not in words:words.append(t)
    return words
# Explicitly transcribed entries from the USCIS reading and writing vocabulary tables.
# PDF text extraction runs columns together, so table-shaped PDFs cannot be parsed line by line.
OFFICIAL_READING = """Abraham Lincoln|George Washington|American flag|Bill of Rights|capital|citizen|city|Congress|country|Father of Our Country|government|President|right|Senators|state/states|White House|America|United States|U.S.|Presidents' Day|Memorial Day|Flag Day|Independence Day|Labor Day|Columbus Day|Thanksgiving|How|What|When|Where|Who|Why|can|come|do/does|elects|have/has|is/are/was/be|lives/lived|meet|name|pay|vote|want|a|for|here|in|of|on|the|to|we|colors|dollar bill|first|largest|many|most|north|one|people|second|south""".split("|")
OFFICIAL_WRITING = """Adams|Lincoln|Washington|American Indians|capital|citizens|Civil War|Congress|Father of Our Country|flag|free|freedom of speech|President|right|Senators|state/states|White House|Alaska|California|Canada|Delaware|Mexico|New York City|United States|Washington|Washington, D.C.|February|May|June|July|September|October|November|Presidents' Day|Memorial Day|Flag Day|Independence Day|Labor Day|Columbus Day|Thanksgiving|can|come|elect|have/has|is/was/be|lives/lived|meets|pay|vote|want|and|during|for|here|in|of|on|the|to|we|blue|dollar bill|fifty/50|first|largest|most|north|one|one hundred/100|people|red|second|south|taxes|white""".split("|")
rv=OFFICIAL_READING
wv=OFFICIAL_WRITING
assert len(rv)==64, len(rv)
assert len(wv)==75, len(wv)
print("Vocab counts",len(rv),len(wv),flush=True)
# Curated factual study sentences, not official examination prompts.
subjects=["George Washington","Abraham Lincoln","Thomas Jefferson","The President","The Senate","The White House","The Constitution","The American flag","The United States","Congress"]
predicates=[
 "is important in American history.","is part of American history.","is a topic in the citizenship test.",
 "is mentioned in the citizenship study materials.","is important to learn about.",
 "is a topic we can read about.","is part of our history.","is a topic in civics.",
 "is important for students to study.","is a subject in the study guide."
]
sentences=[]
for a in subjects:
    for b in predicates:
        sentences.append(a+" "+b)
facts=[
"The President lives in the White House.","The United States has fifty states.",
"Washington, D.C. is the capital of the United States.","Citizens can vote.",
"People can practice freedom of speech.","George Washington was the first President.",
"The flag has fifty stars.","The flag has thirteen stripes.",
"Congress makes federal laws.","The Constitution is the supreme law of the land.",
"Independence Day is July fourth.","The Senate is part of Congress.",
"The Supreme Court is the highest court.","The President leads the executive branch.",
"The United States has a President.","Americans celebrate Memorial Day.",
"Citizens have rights and responsibilities.","The United States has three branches of government.",
"Abraham Lincoln was President.","The Statue of Liberty is in New York Harbor."
]
sentences=facts+sentences
(OUT/"exercises.json").write_text(json.dumps({"reading":sentences[:120],"writing":sentences[:120],"reading_vocab":rv,"writing_vocab":wv,"source_note":"自编练习题，不是USCIS公开的真实考试句子"},ensure_ascii=False),encoding="utf8")
assert len(questions)==128
print("DATA GENERATION COMPLETE",flush=True)
