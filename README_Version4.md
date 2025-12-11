```markdown
# Library Management JavaFX App (PDF with logo & colorful background)

This version includes:
- PDF generation with a colored background and header band.
- Included logo at `src/main/resources/logo.png` (automatically used in PDFs).
- Generated PDFs saved under `pdf-reports/` and opened automatically when supported.

How to run
1. Ensure MySQL and DB schema created (see `sql/schema.sql`).
2. Build & run:
   mvn clean javafx:run

Resources
- Logo is included at `src/main/resources/logo.png`. Replace it if you want a different image (PNG recommended).
- Generated PDF files will be in `pdf-reports/`.

Sample output image included in the repository: `sample_output.png`.
```