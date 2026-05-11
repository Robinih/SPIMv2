$xmlFiles = @("c:\Users\Admin\StudioProjects\SPIM9\app\src\main\res\values\strings.xml", "c:\Users\Admin\StudioProjects\SPIM9\app\src\main\res\values-tl\strings.xml")

foreach ($file in $xmlFiles) {
    $content = Get-Content -Path $file -Raw -Encoding UTF8

    # Fix literal newlines in list items (•)
    # E.g. "`n•" to "\n\n•"
    # But wait, literal newlines in regex:
    
    # 1. Replace literal \n followed by • with \n\n•
    # In PowerShell regex, `n is newline.
    $content = [regex]::Replace($content, "`n\s*•", "\n\n•")
    
    # 2. For life cycle strings, we want to split by "Egg:", "Larva:", etc.
    # It's easier to just do simple replacements.
    $stages = @(
        "Egg Mass:", "Egg:", "Egg \(leaf sheath\):", "Egg \(leaf tissue\):", "Egg \(soil pods\):", "Egg \(soil\):", "Egg \(rows\):",
        "Larva \(inside stem\):", "Larva \(6 instars\):", "Larva \(leaf-folder\):", "Larva \(leaf miner\):", "Larva:", "Uod \(sa loob ng tangkay\):", "Uod \(6 na yugto\):", "Uod \(leaf-folder\):", "Uod \(leaf miner\):",
        "Nymph \(5 instars\):", "Nymph:", "Nymph \(Hopper\):",
        "Juvenile:", "Batang kuhol \(Juvenile\):",
        "Pupa \(inside stem\):", "Pupa \(soil\):", "Pupa:", "Pupa \(sa loob ng tangkay\):", "Pupa \(sa lupa\):",
        "Adult Moth:", "Adult:", "Adult Butterfly:", "Adult Snail:", "Adult Beetle:", "Gamu-gamo:", "Paruparo:", "Salagubang \(Adult\):",
        "Total cycle:", "Kabuuang ikot:", "Itlog:", "Itlog \(sa tangkay ng dahon\):", "Itlog \(sa laman ng dahon\):", "Itlog \(sa lupa\):"
    )
    
    foreach ($stage in $stages) {
        # Negative lookbehinds in .NET regex: (?<!...)
        # We want to replace " Stage:" with "\n\n• Stage:"
        # Only if it is NOT preceded by "• "
        $pattern = "(?<!• |\\n)(" + $stage + ")"
        $replacement = "\n\n• `$1"
        $content = [regex]::Replace($content, $pattern, $replacement)
    }

    # Clean up double \n\n if they happen at the start of a string tag
    $content = [regex]::Replace($content, '">\\n\\n•', '">•')
    # Clean up any \n\n\n
    $content = [regex]::Replace($content, '\\n\\n\\n', '\n\n')

    Set-Content -Path $file -Value $content -Encoding UTF8
}
Write-Output "Done"
