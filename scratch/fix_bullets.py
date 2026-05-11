import re

def process_file(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()

    # We need to process strings that have bullets or lifecycle strings.
    # 1. For bio_controls, cultural, chemical, we will replace literal newlines with \n
    # A literal newline followed by a bullet inside a tag.
    
    # regex to find content inside <string name="...">...</string>
    def string_replacer(match):
        name = match.group(1)
        text = match.group(2)
        
        # 1. If it's a lifecycle string, convert sentences like "Egg: ...", "Larva: ..." to bullets
        if "lifecycle" in name:
            # Add bullets to common stages
            # The pattern is usually something like "Egg: ", "Larva (inside stem): "
            # It starts with a capital letter, maybe some words in parenthesis, followed by a colon.
            # We want to replace these with \n\n• Stage:
            
            # Split the text by these stage names? Or just regex replace.
            # Example: "Egg Mass: Females lay flat... Larva (inside stem): Newly hatched... Pupa (inside stem): Pupation occurs... Adult Moth: Adults emerge... Total cycle: 30–40 days."
            
            # Find all words followed by a colon. 
            # We want to do this carefully so we don't break existing \n if any.
            # Let's clean up existing \n first.
            text = text.replace('\\n', ' ')
            text = re.sub(r'\s+', ' ', text).strip()
            
            # List of possible stages in the text
            stages = [
                "Egg Mass:", "Egg:", "Egg (leaf sheath):", "Egg (leaf tissue):", "Egg (soil pods):", "Egg (soil):", "Egg (rows):",
                "Larva (inside stem):", "Larva (6 instars):", "Larva (leaf-folder):", "Larva (leaf miner):", "Larva:",
                "Nymph (5 instars):", "Nymph:", "Nymph (Hopper):",
                "Juvenile:", "Batang kuhol (Juvenile):",
                "Pupa (inside stem):", "Pupa (soil):", "Pupa:",
                "Adult Moth:", "Adult:", "Adult Butterfly:", "Adult Snail:", "Adult Beetle:", "Gamu-gamo:", "Paruparo:", "Salagubang (Adult):",
                "Total cycle:", "Kabuuang ikot:"
            ]
            
            for stage in stages:
                # Add \n\n• before the stage if it's not at the very beginning of the string
                # and replace the stage with • Stage
                
                # To prevent double replacing if the string already has bullets:
                text = text.replace("• " + stage, stage)
                
                # Replace " Stage:" with "\n\n• Stage:"
                # Use regex to make sure it's not part of another word
                pattern = r'(?<!\n\n• )(?<!\n• )(?<!• )' + re.escape(stage)
                
                def replacement(m):
                    # If it's at the start of the text, don't prepend \n\n
                    if m.start() == 0 or text[:m.start()].strip() == "":
                        return "• " + m.group(0)
                    else:
                        return "\\n\\n• " + m.group(0)
                
                text = re.sub(pattern, replacement, text)
                
            # clean up any leading/trailing space or \n
            text = text.strip()
            
        else:
            # For bio_controls, cultural, chemical, etc.
            # Replace literal newlines and bullet characters.
            # Some strings have literal newlines like:
            # • Conserving...
            # • Protecting...
            
            # Replace \n inside the text with \\n
            # First, temporarily replace literal newlines with \\n
            text = text.replace('\n', '\\n')
            
            # If there's multiple spaces after \\n, clean them up
            text = re.sub(r'\\n\s*•', r'\\n\\n•', text)
            
            # If it already has \\n\\n, don't duplicate
            text = re.sub(r'(\\n)+', r'\\n', text)
            text = text.replace('\\n•', '\\n\\n•')
            
            # clean up leading/trailing
            if text.startswith('\\n'):
                text = text[2:].strip()
                
            # If it starts with • but no newline, that's fine.
            # Sometimes the user just wants the bullets separated by a blank line.
            
        return f'<string name="{name}">{text}</string>'

    pattern = re.compile(r'<string name="([^"]+)">([^<]+)</string>')
    new_content = pattern.sub(string_replacer, content)

    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(new_content)

process_file(r'c:\Users\Admin\StudioProjects\SPIM9\app\src\main\res\values\strings.xml')
process_file(r'c:\Users\Admin\StudioProjects\SPIM9\app\src\main\res\values-tl\strings.xml')
print("Processed both files.")
