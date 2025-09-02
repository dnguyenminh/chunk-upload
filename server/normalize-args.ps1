param([Parameter(ValueFromRemainingArguments=$true)]$args)

# Normalize arguments: preserve --prefixed items, prefix others with --
$normalized = @()
foreach ($a in $args) {
    if ($a -like '--*') { $normalized += $a } else { $normalized += '--' + $a }
}
Write-Output ($normalized -join ' ')
