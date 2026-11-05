import sys
import re

def main():
    with open('app/src/main/java/com/example/ui/screens/ChatScreens.kt', 'r') as f:
        content = f.read()

    # Remove strings and character literals
    content = re.sub(r'"(\\\\|\\"|[^"])*"', '""', content)
    content = re.sub(r"'(\\\\|\\'|[^'])*'", "''", content)
    # Remove single line comments
    content = re.sub(r'//.*', '', content)
    # Remove multi-line comments
    content = re.sub(r'/\*.*?\*/', '', content, flags=re.DOTALL)

    lines = content.split('\n')
    stack = []

    for i, line in enumerate(lines):
        for char in line:
            if char == '{':
                stack.append(i + 1)
            elif char == '}':
                if not stack:
                    print(f"Extra closing brace at line {i + 1}")
                else:
                    stack.pop()
    
    if stack:
        print(f"Missing closing braces for '{'{'}' opened at lines: {stack}")
    else:
        print("Braces are balanced.")

if __name__ == '__main__':
    main()
