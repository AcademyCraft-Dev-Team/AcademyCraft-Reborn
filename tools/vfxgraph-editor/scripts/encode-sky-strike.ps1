param([string]$Python = "python")

# Encode actual VFX editor frames. Requires Pillow in the selected Python environment.
$repoRoot = (Resolve-Path "$PSScriptRoot/../../..").Path
$pythonCode = @'
from pathlib import Path
from PIL import Image
import sys

root = Path(sys.argv[1])
paths = sorted((root / "build/sky-strike-frames").glob("sky_strike_thunderclap_motion_*.png"))
if len(paths) != 52:
    raise SystemExit(f"Expected 52 captured frames, found {len(paths)}")
frames = []
for path in paths:
    with Image.open(path) as image:
        frames.append(image.convert("RGB").resize((480, 640), Image.Resampling.LANCZOS))
output = root / "docs/vfx/sky_strike/sky_strike_loop.gif"
frames[0].save(output, save_all=True, append_images=frames[1:], duration=[80, 80, 90] * 17 + [80],
               loop=0, optimize=True, disposal=2)
print(output)
'@
& $Python -c $pythonCode $repoRoot
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
