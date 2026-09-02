import os, re
pattern = re.compile(r'==\s*".*"|".*"\s*==|!=\s*".*"|".*"\s*!=')
for root, _, files in os.walk('c:/Users/Antonio/OSRS Flipping Friend'):
    for f in files:
        if f.endswith('.java'):
            path = os.path.join(root, f)
            try:
                with open(path, encoding='utf-8') as f_in:
                    for idx, line in enumerate(f_in):
                        if pattern.search(line):
                            print(f"{path}:{idx+1} {line.strip()}")
            except Exception as e:
                pass
