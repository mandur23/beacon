# Beacon ERD - PowerPoint Insert Guide

## Files

| File | Use |
|------|-----|
| `database-erd.png` | **Recommended** - drag into PPT (1920x1080, 16:9) |
| `database-erd.svg` | Vector - Office 2016+ Insert > Pictures |
| `database-erd-ppt.html` | Browser preview / Print to PDF |

## PowerPoint (Windows)

1. Open PowerPoint, add a blank slide (16:9 recommended).
2. **Insert** > **Pictures** > **This Device**
3. Select `docs/database-erd.png`
4. Resize to fill slide; right-click image > **Send to Back** if needed.
5. Optional: right-click > **Compress Pictures** only when file size matters.

## Tips

- PNG keeps fonts and layout consistent on all PCs.
- SVG can be ungrouped in PowerPoint for editing (Office 365).
- For handouts: File > Export > PDF.

## Regenerate PNG

```bash
cd docs
npx @resvg/resvg-js-cli database-erd.svg database-erd.png --fit-width 1920
```
