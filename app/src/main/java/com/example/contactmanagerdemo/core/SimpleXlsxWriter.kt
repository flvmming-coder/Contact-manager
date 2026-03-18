package com.example.contactmanagerdemo.core

import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object SimpleXlsxWriter {

    fun write(file: File, sheetName: String, headers: List<String>, rows: List<List<String>>) {
        FileOutputStream(file).use { fos ->
            ZipOutputStream(fos).use { zip ->
                add(zip, "[Content_Types].xml", contentTypesXml())
                add(zip, "_rels/.rels", rootRelsXml())
                add(zip, "xl/workbook.xml", workbookXml(sheetName))
                add(zip, "xl/_rels/workbook.xml.rels", workbookRelsXml())
                add(zip, "xl/styles.xml", stylesXml())
                add(zip, "xl/worksheets/sheet1.xml", sheetXml(headers, rows))
            }
        }
    }

    private fun add(zip: ZipOutputStream, entryName: String, content: String) {
        zip.putNextEntry(ZipEntry(entryName))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun contentTypesXml(): String {
        return """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
              <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
              <Default Extension="xml" ContentType="application/xml"/>
              <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
              <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
              <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
            </Types>
        """.trimIndent()
    }

    private fun rootRelsXml(): String {
        return """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
            </Relationships>
        """.trimIndent()
    }

    private fun workbookXml(sheetName: String): String {
        return """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                      xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
              <sheets>
                <sheet name="${escapeXml(sheetName)}" sheetId="1" r:id="rId1"/>
              </sheets>
            </workbook>
        """.trimIndent()
    }

    private fun workbookRelsXml(): String {
        return """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
              <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
            </Relationships>
        """.trimIndent()
    }

    private fun stylesXml(): String {
        return """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
              <fonts count="1">
                <font>
                  <sz val="11"/>
                  <name val="Calibri"/>
                </font>
              </fonts>
              <fills count="1">
                <fill>
                  <patternFill patternType="none"/>
                </fill>
              </fills>
              <borders count="1">
                <border>
                  <left/><right/><top/><bottom/><diagonal/>
                </border>
              </borders>
              <cellStyleXfs count="1">
                <xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>
              </cellStyleXfs>
              <cellXfs count="1">
                <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
              </cellXfs>
              <cellStyles count="1">
                <cellStyle name="Normal" xfId="0" builtinId="0"/>
              </cellStyles>
            </styleSheet>
        """.trimIndent()
    }

    private fun sheetXml(headers: List<String>, rows: List<List<String>>): String {
        val builder = StringBuilder()
        builder.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        builder.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        builder.append("<sheetData>")

        var rowIndex = 1
        appendRow(builder, rowIndex, headers)
        rowIndex += 1
        rows.forEach { row ->
            appendRow(builder, rowIndex, row)
            rowIndex += 1
        }

        builder.append("</sheetData>")
        builder.append("</worksheet>")
        return builder.toString()
    }

    private fun appendRow(builder: StringBuilder, rowIndex: Int, values: List<String>) {
        builder.append("""<row r="$rowIndex">""")
        values.forEachIndexed { colIndex, value ->
            val cellRef = columnRef(colIndex) + rowIndex
            builder.append("""<c r="$cellRef" t="inlineStr"><is><t>${escapeXml(value)}</t></is></c>""")
        }
        builder.append("</row>")
    }

    private fun columnRef(index: Int): String {
        var value = index
        val out = StringBuilder()
        do {
            val remainder = value % 26
            out.insert(0, ('A'.code + remainder).toChar())
            value = value / 26 - 1
        } while (value >= 0)
        return out.toString()
    }

    private fun escapeXml(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}

