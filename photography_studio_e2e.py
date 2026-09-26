#!/usr/bin/env python3
"""photography-studio E2E：通过业务应用 SSE 代理调用 DSH Agent，验证 5 个工具全链路。"""
import json, subprocess, sys

AGENT = "photo-copilot"
URL = "http://127.0.0.1:18114/api/assistant/stream"

CASES = [
    ("T1 套餐查询", "影楼有什么拍摄套餐？个人写真多少钱？简洁回答", ["个人写真", "688"]),
    ("T2 摄影师查询", "影楼哪位摄影师擅长亲子？评分多少？简洁回答", ["莉娜", "亲子"]),
    ("T3 预约拍摄", "我是测试客户苏珊，电话13200008888，想约影楼的情侣写真，周五下午 2 点，帮我预约，告诉我订单号和价格", ["F5", "情侣"]),
    ("T4 订单查询", "查一下影楼订单 F5001，谁订的？简洁回答", ["滕先生", "写真"]),
    ("T5 运营统计", "影楼今天运营情况怎么样？多少预约？简洁回答", ["预约", "营收"]),
]

def ask(message, timeout=170):
    payload = json.dumps({"message": message}, ensure_ascii=False)
    try:
        out = subprocess.run(
            ["curl", "-s", "--noproxy", "*", "-N", "-X", "POST", URL,
             "-H", "Content-Type: application/json", "-d", payload,
             "--max-time", str(timeout)],
            capture_output=True, text=True, timeout=timeout + 10).stdout
    except Exception as e:
        return "", f"curl 异常: {e}"
    text = []
    ev = ""
    for line in out.splitlines():
        line = line.rstrip("\r")
        if line.startswith("event:"):
            ev = line[6:].strip()
        elif line.startswith("data:"):
            s = line[5:].strip()
            if not s or s == "[DONE]" or ev != "chunk":
                continue
            try:
                j = json.loads(s)
                c = j.get("content", "")
                if c:
                    text.append(c)
            except Exception:
                pass
            ev = ""
    return "".join(text), out

def main():
    only = sys.argv[1] if len(sys.argv) > 1 else None
    cases = CASES if not only else [c for c in CASES if c[0].startswith(only)]
    passed, failed = 0, []
    for name, q, keys in cases:
        reply, raw = ask(q)
        ok = all(k in reply for k in keys)
        print(f"[{'PASS' if ok else 'FAIL'}] {name}\n  Q: {q}\n  A: {reply[:200]}")
        if ok:
            passed += 1
        else:
            failed.append(name)
            if not reply:
                print(f"  raw 首行: {raw.splitlines()[:3] if raw else '(空)'}")
    print(f"\n===== photography-studio E2E: {passed}/{len(cases)} PASS =====")
    if failed:
        print("失败用例:", ", ".join(failed))
        sys.exit(1)

if __name__ == "__main__":
    main()
