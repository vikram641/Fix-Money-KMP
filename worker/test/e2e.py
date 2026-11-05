import json, os, sys, subprocess, urllib.request, urllib.error, uuid
BASE=os.environ.get("FIXMONEY_WORKER","http://127.0.0.1:8787"); S=sys.argv[1]
# The signer lives next to this file; S is only the scratch dir holding the key and the mock flag.
SIGNER=os.path.join(os.path.dirname(os.path.abspath(__file__)), "ssvkeys.mjs")
def call(method, path, body=None, token=None, headers=None):
    h={"Content-Type":"application/json"}; h.update(headers or {})
    if token: h["Authorization"]="Bearer "+token
    req=urllib.request.Request(BASE+path, data=json.dumps(body).encode() if body is not None else None, method=method, headers=h)
    try:
        r=urllib.request.urlopen(req); return r.status, json.loads(r.read() or b"null"), dict(r.headers)
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read() or b"null"), dict(e.headers)
def flag(m): open(S+"/flag","w").write(m)
def chat(tok, rid=None, feature="fixoo"):
    return call("POST","/v1/chat",{"messages":[{"role":"user","content":"hi"}]},tok,{"X-Feature":feature,"X-Request-Id":rid or uuid.uuid4().hex})
def check(name, cond, info=""):
    print(("PASS " if cond else "FAIL ")+name, "" if cond else info); 
    if not cond: check.failed=True
check.failed=False
mode=sys.argv[2]
dev="dev-"+uuid.uuid4().hex
st,b,_=call("POST","/v1/register",{"deviceKey":dev,"platform":"android"}); tok=b["token"]; acct=b["accountId"]
check("register 200", st==200, b)
st2,b2,_=call("POST","/v1/register",{"deviceKey":dev,"platform":"android"})
check("reinstall (no token, same device) -> same account", b2["accountId"]==acct, b2)
st3,b3,_=call("POST","/v1/register",{"deviceKey":"other-"+uuid.uuid4().hex},token=tok)
check("new phone with restored token -> same account", b3["accountId"]==acct and b3["token"]==tok, b3)
check("bad token on wallet -> 401", call("GET","/v1/wallet",token=tok+"x")[0]==401)
check("legacy token-less chat still works", call("POST","/v1/chat",{"messages":[{"role":"user","content":"hi"}]})[0]==200)
if mode=="trial":
    w=b["wallet"]; check("trial active, 0 coins", w["trialActive"] and w["coins"]==0, w)
    flag("ok"); st,r,h=chat(tok)
    check("trial chat 200 + headers", st==200 and h.get("X-Trial-Uses-Left")=="29", (st,h))
    rid=uuid.uuid4().hex; chat(tok,rid); st,r,h=chat(tok,rid)
    check("retry same request id not counted twice", h.get("X-Trial-Uses-Left")=="28", h)
    flag("busy"); st,r,h=chat(tok)
    check("busy -> 503 AI_BUSY, not counted", st==503 and r["code"]=="AI_BUSY" and h.get("X-Trial-Uses-Left")=="28", (st,r,h))
    flag("bad"); st,r,h=chat(tok)
    check("provider 400 passed through, not counted", st==400 and h.get("X-Trial-Uses-Left")=="28", (st,h))
    flag("ok")
    st,r,_=call("POST","/v1/spend",{"feature":"insight","requestId":uuid.uuid4().hex},tok)
    check("insight spend free (cost 0)", st==200 and r["wallet"]["trialUsesLeftToday"]==28, r)
else:
    w=b["wallet"]; check("trial over -> welcome bonus 10", (not w["trialActive"]) and w["coins"]==10 and w.get("welcomeBonusGranted")==10, w)
    st,r,_=call("GET","/v1/wallet",token=tok); check("bonus only once", r["wallet"]["coins"]==10 and "welcomeBonusGranted" not in r["wallet"], r)
    flag("ok"); st,r,h=chat(tok); check("paid chat 200, 9 left", st==200 and h.get("X-Coins")=="9", (st,h))
    flag("busy"); st,r,h=chat(tok); check("busy -> not charged", st==503 and r["charged"] is False and h.get("X-Coins")=="9", (st,r,h))
    flag("ok")
    for i in range(9): chat(tok)
    st,r,_=chat(tok); check("0 coins -> 402 NO_COINS", st==402 and r["code"]=="NO_COINS", (st,r))
    # Fixoo Premium (DEV_MODE accepts dev-premium-* tokens in place of a Play purchase)
    st,r,_=call("POST","/v1/premium/verify",{"purchaseToken":"short"},tok); check("premium: bad token -> 400", st==400, (st,r))
    st,r,_=call("POST","/v1/premium/verify",{"purchaseToken":"dev-premium-"+uuid.uuid4().hex},tok)
    check("premium: verify -> active", st==200 and r["wallet"]["premiumActive"] and r["wallet"]["premiumUsesLeftToday"]==100, (st,r))
    st,r,h=chat(tok); check("premium: AI at 0 coins -> 200, still 0 coins", st==200 and h.get("X-Coins")=="0", (st,h))
    st,r,_=call("GET","/v1/wallet",token=tok); check("premium: fair-use counted", r["wallet"]["premiumUsesLeftToday"]==99, r)
    # SSV
    q=subprocess.check_output(["node",SIGNER,S,"sign",acct,"tx-1"]).decode().strip()
    st,r,_=call("GET","/v1/admob/ssv?"+q); check("signed ssv credits 5", st==200 and r["credited"]==5, (st,r))
    st,r,_=call("GET","/v1/admob/ssv?"+q); check("same transaction twice -> duplicate", r["credited"]==0 and r["reason"]=="DUPLICATE", r)
    tampered=q.replace("reward_amount=5","reward_amount=500")
    st,r,_=call("GET","/v1/admob/ssv?"+tampered); check("tampered ssv -> 400", st==400, (st,r))
    st,r,_=call("GET","/v1/wallet",token=tok); check("balance 5, ads left 9, cap 10", r["wallet"]["coins"]==5 and r["wallet"]["adsLeftToday"]==9 and r["wallet"]["dailyAdCap"]==10, r)
    for i in range(2,12):
        q=subprocess.check_output(["node",SIGNER,S,"sign",acct,f"tx-{i}"]).decode().strip(); st,r,_=call("GET","/v1/admob/ssv?"+q)
    check("11th video capped", r["credited"]==0 and r["reason"]=="DAILY_CAP", r)
    st,r,_=call("GET","/v1/wallet",token=tok); check("balance after 10 videos = 50", r["wallet"]["coins"]==50 and r["wallet"]["adsLeftToday"]==0, r)
sys.exit(1 if check.failed else 0)
