package com.pdfmaster.ai

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.math.min

private enum class PdfAction {
    NONE,
    READ,
    MERGE,
    SPLIT,
    COMPRESS,
    IMAGES,
    PDF_TO_WORD,
    WORD_TO_PDF
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        PDFBoxResourceLoader.init(applicationContext)

        setContent {
            MaterialTheme {
                PdfMasterScreen()
            }
        }
    }
}

@Composable
private fun PdfMasterScreen() {

    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as ComponentActivity
    val resolver = context.contentResolver

    var language by remember { mutableStateOf("ar") }
    var action by remember { mutableStateOf(PdfAction.NONE) }
    var selectedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }

    var readerUri by remember { mutableStateOf<Uri?>(null) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var currentPage by remember { mutableIntStateOf(0) }
    var pageCount by remember { mutableIntStateOf(0) }

    var startPage by remember { mutableStateOf("1") }
    var endPage by remember { mutableStateOf("1") }

    var status by remember {
        mutableStateOf("اختار الخدمة اللي بغيتي")
    }

    var busy by remember { mutableStateOf(false) }

    val isArabic = language == "ar"

    fun runTask(task: () -> String) {

        if (busy) return

        busy = true
        status = if (isArabic) {
            "جاري المعالجة..."
        } else {
            "Processing..."
        }

        Thread {

            val result = try {
                task()
            } catch (e: Exception) {
                "Error: ${e.message ?: "Unknown error"}"
            }

            activity.runOnUiThread {
                status = result
                busy = false
            }

        }.start()
    }

    fun openReaderPage(uri: Uri, pageIndex: Int) {

        if (busy) return

        runTask {

            val result = renderPdfPage(
                resolver,
                uri,
                pageIndex
            )

            activity.runOnUiThread {
                preview = result.first
                currentPage = pageIndex
                pageCount = result.second
            }

            if (isArabic) {
                "الصفحة ${pageIndex + 1} من ${result.second}"
            } else {
                "Page ${pageIndex + 1} of ${result.second}"
            }
        }
    }

    // PDF output picker
    val pdfOutputPicker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/pdf")
        ) { outputUri ->

            if (outputUri != null) {

                val uris = selectedUris
                val selectedAction = action
                val firstPage = startPage.toIntOrNull()
                val lastPage = endPage.toIntOrNull()

                runTask {

                    when (selectedAction) {

                        PdfAction.MERGE -> {
                            mergePdfs(
                                resolver,
                                uris,
                                outputUri
                            )
                        }

                        PdfAction.SPLIT -> {

                            val first = firstPage
                                ?: throw IllegalArgumentException(
                                    "Invalid first page"
                                )

                            val last = lastPage
                                ?: throw IllegalArgumentException(
                                    "Invalid last page"
                                )

                            if (uris.isEmpty()) {
                                throw IllegalArgumentException(
                                    "No PDF selected"
                                )
                            }

                            splitPdf(
                                resolver,
                                uris.first(),
                                outputUri,
                                first,
                                last
                            )
                        }

                        PdfAction.COMPRESS -> {

                            if (uris.isEmpty()) {
                                throw IllegalArgumentException(
                                    "No PDF selected"
                                )
                            }

                            compressPdf(
                                resolver,
                                uris.first(),
                                outputUri
                            )
                        }

                        PdfAction.IMAGES -> {
                            imagesToPdf(
                                resolver,
                                uris,
                                outputUri
                            )
                        }

                        PdfAction.WORD_TO_PDF -> {

                            if (uris.isEmpty()) {
                                throw IllegalArgumentException(
                                    "No Word file selected"
                                )
                            }

                            wordToPdf(
                                resolver,
                                uris.first(),
                                outputUri
                            )
                        }

                        else -> "No operation selected"
                    }
                }

            } else {
                status = if (isArabic) {
                    "تم إلغاء اختيار مكان الحفظ"
                } else {
                    "Saving cancelled"
                }
            }
        }

    // DOCX output picker
    val wordOutputPicker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            )
        ) { outputUri ->

            if (outputUri != null) {

                val uris = selectedUris

                runTask {

                    if (uris.isEmpty()) {
                        throw IllegalArgumentException(
                            "No PDF selected"
                        )
                    }

                    pdfToWord(
                        resolver,
                        uris.first(),
                        outputUri
                    )
                }

            } else {
                status = if (isArabic) {
                    "تم إلغاء اختيار مكان الحفظ"
                } else {
                    "Saving cancelled"
                }
            }
        }

    val pdfPicker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->

            if (uri != null) {

                when (action) {

                    PdfAction.READ -> {
                        readerUri = uri
                        preview = null
                        currentPage = 0
                        pageCount = 0

                        openReaderPage(uri, 0)
                    }

                    PdfAction.SPLIT -> {
                        selectedUris = listOf(uri)
                        pdfOutputPicker.launch("split.pdf")
                    }

                    PdfAction.COMPRESS -> {
                        selectedUris = listOf(uri)
                        pdfOutputPicker.launch("compressed.pdf")
                    }

                    PdfAction.PDF_TO_WORD -> {
                        selectedUris = listOf(uri)
                        wordOutputPicker.launch("converted.docx")
                    }

                    else -> Unit
                }
            }
        }

    val wordPicker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->

            if (uri != null) {
                selectedUris = listOf(uri)
                pdfOutputPicker.launch("converted.pdf")
            }
        }

    val multiPdfPicker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenMultipleDocuments()
        ) { uris ->

            if (uris.isNotEmpty()) {
                selectedUris = uris
                pdfOutputPicker.launch("merged.pdf")
            }
        }

    val imagePicker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenMultipleDocuments()
        ) { uris ->

            if (uris.isNotEmpty()) {
                selectedUris = uris
                pdfOutputPicker.launch("images.pdf")
            }
        }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF101820)
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {

                Text(
                    text = "PDF Master AI",
                    color = Color(0xFF00D68F),
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold
                )

                TextButton(
                    onClick = {
                        language = when (language) {
                            "ar" -> "fr"
                            "fr" -> "en"
                            else -> "ar"
                        }
                    }
                ) {
                    Text(
                        text = when (language) {
                            "ar" -> "AR"
                            "fr" -> "FR"
                            else -> "EN"
                        },
                        color = Color.White
                    )
                }
            }

            Text(
                text = when (language) {
                    "ar" -> "جميع أدوات PDF في تطبيق واحد"
                    "fr" -> "Tous vos outils PDF dans une application"
                    else -> "All PDF tools in one app"
                },
                color = Color.LightGray,
                fontSize = 14.sp
            )

            Spacer(Modifier.height(20.dp))

            ServiceButton(
                title = when (language) {
                    "ar" -> "قراءة PDF"
                    "fr" -> "Lire PDF"
                    else -> "Read PDF"
                },
                description = when (language) {
                    "ar" -> "فتح وقراءة ملف PDF"
                    "fr" -> "Ouvrir et lire un fichier PDF"
                    else -> "Open and read a PDF file"
                },
                enabled = !busy
            ) {
                action = PdfAction.READ
                pdfPicker.launch(arrayOf("application/pdf"))
            }

            ServiceButton(
                title = when (language) {
                    "ar" -> "دمج ملفات PDF"
                    "fr" -> "Fusionner les PDF"
                    else -> "Merge PDFs"
                },
                description = when (language) {
                    "ar" -> "جمع عدة ملفات في ملف واحد"
                    "fr" -> "Combiner plusieurs fichiers"
                    else -> "Combine multiple PDF files"
                },
                enabled = !busy
            ) {
                action = PdfAction.MERGE
                multiPdfPicker.launch(arrayOf("application/pdf"))
            }

            ServiceButton(
                title = when (language) {
                    "ar" -> "تقسيم PDF"
                    "fr" -> "Diviser PDF"
                    else -> "Split PDF"
                },
                description = when (language) {
                    "ar" -> "استخراج صفحات محددة"
                    "fr" -> "Extraire des pages spécifiques"
                    else -> "Extract selected pages"
                },
                enabled = !busy
            ) {
                action = PdfAction.SPLIT
            }

            if (action == PdfAction.SPLIT) {

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF1C2935)
                    )
                ) {

                    Column(Modifier.padding(14.dp)) {

                        Text(
                            text = if (isArabic) {
                                "حدد الصفحات التي تريد استخراجها"
                            } else {
                                "Select the pages to extract"
                            },
                            color = Color.White
                        )

                        Spacer(Modifier.height(8.dp))

                        OutlinedTextField(
                            value = startPage,
                            onValueChange = {
                                startPage = it.filter(Char::isDigit)
                            },
                            label = {
                                Text(
                                    if (isArabic) "من الصفحة"
                                    else "From page"
                                )
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number
                            ),
                            singleLine = true
                        )

                        Spacer(Modifier.height(8.dp))

                        OutlinedTextField(
                            value = endPage,
                            onValueChange = {
                                endPage = it.filter(Char::isDigit)
                            },
                            label = {
                                Text(
                                    if (isArabic) "إلى الصفحة"
                                    else "To page"
                                )
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number
                            ),
                            singleLine = true
                        )

                        Spacer(Modifier.height(10.dp))

                        Button(
                            enabled = !busy,
                            onClick = {

                                val first = startPage.toIntOrNull()
                                val last = endPage.toIntOrNull()

                                if (
                                    first == null ||
                                    last == null ||
                                    first < 1 ||
                                    last < first
                                ) {

                                    status = if (isArabic) {
                                        "أدخل أرقام صفحات صحيحة"
                                    } else {
                                        "Enter a valid page range"
                                    }

                                } else {

                                    action = PdfAction.SPLIT

                                    pdfPicker.launch(
                                        arrayOf("application/pdf")
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (isArabic) {
                                    "اختار PDF وقسمو"
                                } else {
                                    "Choose PDF and split"
                                }
                            )
                        }
                    }
                }
            }

            ServiceButton(
                title = when (language) {
                    "ar" -> "ضغط PDF"
                    "fr" -> "Compresser PDF"
                    else -> "Compress PDF"
                },
                description = when (language) {
                    "ar" -> "تقليل الحجم مع احتمال نقص الجودة"
                    "fr" -> "Réduire la taille avec perte possible"
                    else -> "Reduce size with possible quality loss"
                },
                enabled = !busy
            ) {
                action = PdfAction.COMPRESS
                pdfPicker.launch(arrayOf("application/pdf"))
            }

            ServiceButton(
                title = when (language) {
                    "ar" -> "تحويل الصور إلى PDF"
                    "fr" -> "Images vers PDF"
                    else -> "Images to PDF"
                },
                description = when (language) {
                    "ar" -> "جمع الصور في ملف PDF"
                    "fr" -> "Regrouper les images dans un PDF"
                    else -> "Combine images into a PDF"
                },
                enabled = !busy
            ) {
                action = PdfAction.IMAGES
                imagePicker.launch(arrayOf("image/*"))
            }

            ServiceButton(
                title = when (language) {
                    "ar" -> "PDF إلى Word"
                    "fr" -> "PDF vers Word"
                    else -> "PDF to Word"
                },
                description = when (language) {
                    "ar" -> "تحويل صفحات PDF إلى Word مع الحفاظ على شكلها"
                    "fr" -> "Convertir les pages PDF en images Word"
                    else -> "Convert PDF pages into Word images"
                },
                enabled = !busy
            ) {
                action = PdfAction.PDF_TO_WORD
                pdfPicker.launch(arrayOf("application/pdf"))
            }

            ServiceButton(
                title = when (language) {
                    "ar" -> "Word إلى PDF"
                    "fr" -> "Word vers PDF"
                    else -> "Word to PDF"
                },
                description = when (language) {
                    "ar" -> "تحويل النصوص والجداول والصور الأساسية"
                    "fr" -> "Convertir le texte, les tableaux et les images"
                    else -> "Convert text, tables and basic images"
                },
                enabled = !busy
            ) {
                action = PdfAction.WORD_TO_PDF

                wordPicker.launch(
                    arrayOf(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                    )
                )
            }

            Spacer(Modifier.height(18.dp))

            if (busy) {
                CircularProgressIndicator(
                    color = Color(0xFF00D68F)
                )
                Spacer(Modifier.height(10.dp))
            }

            Text(
                text = status,
                color = Color(0xFF00D68F),
                fontSize = 14.sp
            )

            if (preview != null) {

                Spacer(Modifier.height(20.dp))

                Text(
                    text = if (isArabic) "معاينة PDF" else "PDF Preview",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )

                Image(
                    bitmap = preview!!.asImageBitmap(),
                    contentDescription = "PDF page preview",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 500.dp)
                )

                Text(
                    text = "${currentPage + 1} / $pageCount",
                    color = Color.White
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {

                    Button(
                        enabled = !busy && currentPage > 0,
                        onClick = {
                            val uri = readerUri
                            if (uri != null) {
                                openReaderPage(uri, currentPage - 1)
                            }
                        }
                    ) {
                        Text(if (isArabic) "السابق" else "Previous")
                    }

                    Button(
                        enabled = !busy && currentPage < pageCount - 1,
                        onClick = {
                            val uri = readerUri
                            if (uri != null) {
                                openReaderPage(uri, currentPage + 1)
                            }
                        }
                    ) {
                        Text(if (isArabic) "التالي" else "Next")
                    }
                }
            }

            Spacer(Modifier.height(25.dp))

            Text(
                text = "PDF Master AI",
                color = Color.Gray,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun ServiceButton(
    title: String,
    description: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1C2935)
        )
    ) {

        Column(
            modifier = Modifier.padding(12.dp)
        ) {

            Button(
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00A878)
                )
            ) {
                Text(title, color = Color.White)
            }

            Text(
                text = description,
                color = Color.LightGray,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

private fun renderPdfPage(
    resolver: ContentResolver,
    uri: Uri,
    pageIndex: Int
): Pair<Bitmap, Int> {

    val descriptor = resolver.openFileDescriptor(uri, "r")
        ?: throw IllegalArgumentException("Cannot open PDF")

    descriptor.use { pfd ->

        PdfRenderer(pfd).use { renderer ->

            require(pageIndex in 0 until renderer.pageCount) {
                "Page does not exist"
            }

            renderer.openPage(pageIndex).use { page ->

                val width = 1200

                val height = (
                    width.toFloat() * page.height / page.width
                ).toInt().coerceAtLeast(1)

                val bitmap = Bitmap.createBitmap(
                    width,
                    height,
                    Bitmap.Config.ARGB_8888
                )

                page.render(
                    bitmap,
                    null,
                    null,
                    PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                )

                return bitmap to renderer.pageCount
            }
        }
    }
}

private fun mergePdfs(
    resolver: ContentResolver,
    sources: List<Uri>,
    outputUri: Uri
): String {

    require(sources.size >= 2) {
        "Choose at least two PDF files"
    }

    val destination = PDDocument()

    try {

        for (uri in sources) {

            val input = resolver.openInputStream(uri)
                ?: throw IllegalArgumentException("Cannot open source PDF")

            input.use { stream ->

                PDDocument.load(stream).use { source ->

                    for (index in 0 until source.numberOfPages) {
                        destination.importPage(source.getPage(index))
                    }
                }
            }
        }

        val output = resolver.openOutputStream(outputUri)
            ?: throw IllegalArgumentException("Cannot create output file")

        output.use { stream ->
            destination.save(stream)
        }

    } finally {
        destination.close()
    }

    return "PDF files merged successfully"
}

private fun splitPdf(
    resolver: ContentResolver,
    sourceUri: Uri,
    outputUri: Uri,
    firstPage: Int,
    lastPage: Int
): String {

    val input = resolver.openInputStream(sourceUri)
        ?: throw IllegalArgumentException("Cannot open PDF")

    input.use { stream ->

        PDDocument.load(stream).use { source ->

            require(firstPage >= 1 && lastPage >= firstPage) {
                "Invalid page range"
            }

            require(lastPage <= source.numberOfPages) {
                "Last page exceeds PDF page count (${source.numberOfPages})"
            }

            val outputDocument = PDDocument()

            try {

                for (index in (firstPage - 1) until lastPage) {
                    outputDocument.importPage(source.getPage(index))
                }

                val output = resolver.openOutputStream(outputUri)
                    ?: throw IllegalArgumentException("Cannot create output file")

                output.use { destination ->
                    outputDocument.save(destination)
                }

            } finally {
                outputDocument.close()
            }
        }
    }

    return "PDF pages extracted successfully"
}

private fun compressPdf(
    resolver: ContentResolver,
    sourceUri: Uri,
    outputUri: Uri
): String {

    val descriptor = resolver.openFileDescriptor(sourceUri, "r")
        ?: throw IllegalArgumentException("Cannot open PDF")

    descriptor.use { pfd ->

        PdfRenderer(pfd).use { renderer ->

            val outputDocument = PdfDocument()

            try {

                for (index in 0 until renderer.pageCount) {

                    renderer.openPage(index).use { page ->

                        val width = 1000

                        val height = (
                            width.toFloat() * page.height / page.width
                        ).toInt().coerceIn(1, 2000)

                        val bitmap = Bitmap.createBitmap(
                            width,
                            height,
                            Bitmap.Config.RGB_565
                        )

                        try {

                            page.render(
                                bitmap,
                                null,
                                null,
                                PdfRenderer.Page.RENDER_MODE_FOR_PRINT
                            )

                            val pageWidth = 595

                            val pageHeight = (
                                pageWidth.toFloat() * page.height / page.width
                            ).toInt().coerceAtLeast(1)

                            val info = PdfDocument.PageInfo.Builder(
                                pageWidth,
                                pageHeight,
                                index + 1
                            ).create()

                            val newPage = outputDocument.startPage(info)

                            newPage.canvas.drawBitmap(
                                bitmap,
                                null,
                                Rect(0, 0, pageWidth, pageHeight),
                                null
                            )

                            outputDocument.finishPage(newPage)

                        } finally {
                            bitmap.recycle()
                        }
                    }
                }

                val output = resolver.openOutputStream(outputUri)
                    ?: throw IllegalArgumentException("Cannot create output file")

                output.use { stream ->
                    outputDocument.writeTo(stream)
                }

            } finally {
                outputDocument.close()
            }
        }
    }

    return "PDF converted to image-based PDF"
}

private fun imagesToPdf(
    resolver: ContentResolver,
    imageUris: List<Uri>,
    outputUri: Uri
): String {

    require(imageUris.isNotEmpty()) {
        "Choose at least one image"
    }

    val document = PdfDocument()

    try {

        imageUris.forEachIndexed { index, uri ->

            val input = resolver.openInputStream(uri)
                ?: throw IllegalArgumentException("Cannot open image")

            val bitmap = input.use {
                BitmapFactory.decodeStream(it)
            } ?: throw IllegalArgumentException("Unsupported image")

            try {

                val pageWidth = 595
                val pageHeight = 842

                val info = PdfDocument.PageInfo.Builder(
                    pageWidth,
                    pageHeight,
                    index + 1
                ).create()

                val page = document.startPage(info)

                val scale = min(
                    pageWidth.toFloat() / bitmap.width,
                    pageHeight.toFloat() / bitmap.height
                )

                val drawWidth = (bitmap.width * scale).toInt()
                val drawHeight = (bitmap.height * scale).toInt()

                val left = (pageWidth - drawWidth) / 2
                val top = (pageHeight - drawHeight) / 2

                page.canvas.drawBitmap(
                    bitmap,
                    null,
                    Rect(
                        left,
                        top,
                        left + drawWidth,
                        top + drawHeight
                    ),
                    null
                )

                document.finishPage(page)

            } finally {
                bitmap.recycle()
            }
        }

        val output = resolver.openOutputStream(outputUri)
            ?: throw IllegalArgumentException("Cannot create output file")

        output.use { stream ->
            document.writeTo(stream)
        }

    } finally {
        document.close()
    }

    return "Images converted to PDF successfully"
}

// PDF TO WORD
// Each PDF page is inserted as a high-resolution image.
// This preserves the visual appearance of text, tables and pictures.

private fun pdfToWord(
    resolver: ContentResolver,
    pdfUri: Uri,
    outputUri: Uri
): String {

    val descriptor = resolver.openFileDescriptor(pdfUri, "r")
        ?: throw IllegalArgumentException("Cannot open PDF")

    descriptor.use { pfd ->

        PdfRenderer(pfd).use { renderer ->

            require(renderer.pageCount > 0) {
                "PDF has no pages"
            }

            val output = resolver.openOutputStream(outputUri)
                ?: throw IllegalArgumentException("Cannot create DOCX")

            output.use { stream ->

                ZipOutputStream(stream).use { zip ->

                    val relationships = StringBuilder()
                    val body = StringBuilder()

                    body.append(
                        """<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture"><w:body>"""
                    )

                    for (index in 0 until renderer.pageCount) {

                        renderer.openPage(index).use { page ->

                            val width = 1100

                            val height = (
                                width.toFloat() * page.height / page.width
                            ).toInt().coerceAtLeast(1)

                            val bitmap = Bitmap.createBitmap(
                                width,
                                height,
                                Bitmap.Config.ARGB_8888
                            )

                            try {

                                page.render(
                                    bitmap,
                                    null,
                                    null,
                                    PdfRenderer.Page.RENDER_MODE_FOR_PRINT
                                )

                                val imageStream = ByteArrayOutputStream()

                                bitmap.compress(
                                    Bitmap.CompressFormat.PNG,
                                    100,
                                    imageStream
                                )

                                val imageName = "page${index + 1}.png"
                                val relationId = "rId${index + 1}"

                                zip.putNextEntry(
                                    ZipEntry("word/media/$imageName")
                                )

                                zip.write(imageStream.toByteArray())
                                zip.closeEntry()

                                relationships.append(
                                    """<Relationship Id="$relationId" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/$imageName"/>"""
                                )

                                val maxWidth = 5943600L
                                val maxHeight = 8380000L

                                val ratio = min(
                                    maxWidth.toFloat() / width,
                                    maxHeight.toFloat() / height
                                )

                                val imageWidth = (width * ratio).toLong()
                                val imageHeight = (height * ratio).toLong()

                                if (index > 0) {
                                    body.append(
                                        """<w:p><w:r><w:br w:type="page"/></w:r></w:p>"""
                                    )
                                }

                                body.append(
                                    """
                                    <w:p>
                                      <w:pPr><w:jc w:val="center"/></w:pPr>
                                      <w:r>
                                        <w:drawing>
                                          <wp:inline>
                                            <wp:extent cx="$imageWidth" cy="$imageHeight"/>
                                            <wp:docPr id="${index + 1}" name="PDF Page ${index + 1}"/>
                                            <a:graphic>
                                              <a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">
                                                <pic:pic>
                                                  <pic:nvPicPr>
                                                    <pic:cNvPr id="${index + 1}" name="$imageName"/>
                                                    <pic:cNvPicPr/>
                                                  </pic:nvPicPr>
                                                  <pic:blipFill>
                                                    <a:blip r:embed="$relationId"/>
                                                    <a:stretch><a:fillRect/></a:stretch>
                                                  </pic:blipFill>
                                                  <pic:spPr>
                                                    <a:xfrm>
                                                      <a:off x="0" y="0"/>
                                                      <a:ext cx="$imageWidth" cy="$imageHeight"/>
                                                    </a:xfrm>
                                                    <a:prstGeom prst="rect"><a:avLst/></a:prstGeom>
                                                  </pic:spPr>
                                                </pic:pic>
                                              </a:graphicData>
                                            </a:graphic>
                                          </wp:inline>
                                        </w:drawing>
                                      </w:r>
                                    </w:p>
                                    """.trimIndent()
                                )

                            } finally {
                                bitmap.recycle()
                            }
                        }
                    }

                    body.append(
                        """<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="500" w:right="500" w:bottom="500" w:left="500"/></w:sectPr></w:body></w:document>"""
                    )

                    addZipEntry(
                        zip,
                        "[Content_Types].xml",
                        """<?xml version="1.0" encoding="UTF-8"?>
                        <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                        <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                        <Default Extension="xml" ContentType="application/xml"/>
                        <Default Extension="png" ContentType="image/png"/>
                        <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                        </Types>"""
                    )

                    addZipEntry(
                        zip,
                        "_rels/.rels",
                        """<?xml version="1.0" encoding="UTF-8"?>
                        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                        <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                        </Relationships>"""
                    )

                    addZipEntry(
                        zip,
                        "word/_rels/document.xml.rels",
                        """<?xml version="1.0" encoding="UTF-8"?>
                        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                        $relationships
                        </Relationships>"""
                    )

                    addZipEntry(
                        zip,
                        "word/document.xml",
                        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                        $body"""
                    )
                }
            }
        }
    }

    return "PDF converted to Word successfully"
}

// WORD TO PDF
// Extracts paragraphs, basic table text and embedded images.

private fun wordToPdf(
    resolver: ContentResolver,
    docxUri: Uri,
    outputUri: Uri
): String {

    val input = resolver.openInputStream(docxUri)
        ?: throw IllegalArgumentException("Cannot open Word file")

    val entries = mutableMapOf<String, ByteArray>()

    input.use { source ->

        ZipInputStream(source).use { zip ->

            while (true) {

                val entry = zip.nextEntry ?: break

                if (!entry.isDirectory) {
                    val bytes = ByteArrayOutputStream()
                    zip.copyTo(bytes)
                    entries[entry.name] = bytes.toByteArray()
                }

                zip.closeEntry()
            }
        }
    }

    val xml = entries["word/document.xml"]
        ?: throw IllegalArgumentException("Invalid DOCX file")

    val paragraphs = mutableListOf<String>()
    val imageIds = mutableListOf<String>()

    val factory = XmlPullParserFactory.newInstance()
    val parser = factory.newPullParser()

    parser.setInput(
        ByteArrayInputStream(xml),
        "UTF-8"
    )

    var inTable = false
    var inRow = false
    var inCell = false

    var paragraphText = StringBuilder()
    var cellText = StringBuilder()
    var rowCells = mutableListOf<String>()

    var event = parser.eventType

    while (event != XmlPullParser.END_DOCUMENT) {

        when (event) {

            XmlPullParser.START_TAG -> {

                when (parser.name) {

                    "tbl" -> inTable = true

                    "tr" -> {
                        inRow = true
                        rowCells = mutableListOf()
                    }

                    "tc" -> {
                        inCell = true
                        cellText = StringBuilder()
                    }

                    "p" -> {
                        paragraphText = StringBuilder()
                    }

                    "blip" -> {

                        val id = parser.getAttributeValue(
                            "http://schemas.openxmlformats.org/officeDocument/2006/relationships",
                            "embed"
                        )

                        if (id != null) {
                            imageIds.add(id)
                        }
                    }
                }
            }

            XmlPullParser.TEXT -> {

                if (inCell) {
                    cellText.append(parser.text)
                } else {
                    paragraphText.append(parser.text)
                }
            }

            XmlPullParser.END_TAG -> {

                when (parser.name) {

                    "p" -> {

                        val value = paragraphText.toString().trim()

                        if (value.isNotEmpty() && !inCell) {
                            paragraphs.add(value)
                        }
                    }

                    "tc" -> {
                        rowCells.add(cellText.toString().trim())
                        inCell = false
                    }

                    "tr" -> {

                        if (rowCells.isNotEmpty()) {
                            paragraphs.add(
                                rowCells.joinToString("    |    ")
                            )
                        }

                        inRow = false
                    }

                    "tbl" -> {
                        inTable = false
                    }
                }
            }
        }

        event = parser.next()
    }

    // Read image relationships.

    val relationshipXml = entries["word/_rels/document.xml.rels"]
    val imageTargets = mutableMapOf<String, String>()

    if (relationshipXml != null) {

        val relParser = factory.newPullParser()

        relParser.setInput(
            ByteArrayInputStream(relationshipXml),
            "UTF-8"
        )

        var relEvent = relParser.eventType

        while (relEvent != XmlPullParser.END_DOCUMENT) {

            if (
                relEvent == XmlPullParser.START_TAG &&
                relParser.name == "Relationship"
            ) {

                val id = relParser.getAttributeValue(null, "Id")
                val target = relParser.getAttributeValue(null, "Target")

                if (id != null && target != null) {
                    imageTargets[id] = target
                }
            }

            relEvent = relParser.next()
        }
    }

    val document = PdfDocument()

    try {

        val pageWidth = 595
        val pageHeight = 842
        val margin = 35f

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            textSize = 11f
        }

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            textSize = 14f
            isFakeBoldText = true
        }

        var pageNumber = 1

        var page = document.startPage(
            PdfDocument.PageInfo.Builder(
                pageWidth,
                pageHeight,
                pageNumber
            ).create()
        )

        var canvas = page.canvas
        var y = margin + 15f

        fun newPage() {

            document.finishPage(page)

            pageNumber++

            page = document.startPage(
                PdfDocument.PageInfo.Builder(
                    pageWidth,
                    pageHeight,
                    pageNumber
                ).create()
            )

            canvas = page.canvas
            y = margin + 15f
        }

        val maxWidth = pageWidth - margin * 2

        // Draw extracted text and table rows.

        for (text in paragraphs) {

            val words = text.split(Regex("\\s+"))
            var line = ""

            for (word in words) {

                val candidate = if (line.isEmpty()) {
                    word
                } else {
                    "$line $word"
                }

                if (
                    paint.measureText(candidate) > maxWidth &&
                    line.isNotEmpty()
                ) {

                    if (y > pageHeight - margin) {
                        newPage()
                    }

                    canvas.drawText(
                        line,
                        margin,
                        y,
                        paint
                    )

                    y += 17f
                    line = word

                } else {
                    line = candidate
                }
            }

            if (line.isNotEmpty()) {

                if (y > pageHeight - margin) {
                    newPage()
                }

                canvas.drawText(
                    line,
                    margin,
                    y,
                    paint
                )

                y += 22f
            }
        }

        // Draw embedded images.

        for (id in imageIds.distinct()) {

            val target = imageTargets[id] ?: continue

            val imagePath = if (target.startsWith("/")) {
                target.removePrefix("/")
            } else {
                "word/$target"
            }

            val normalizedPath = imagePath
                .replace("word/../", "")
                .replace("../", "")

            val imageBytes = entries[normalizedPath] ?: continue

            val bitmap = BitmapFactory.decodeByteArray(
                imageBytes,
                0,
                imageBytes.size
            ) ?: continue

            try {

                val scale = min(
                    maxWidth / bitmap.width,
                    250f / bitmap.height
                )

                val width = (bitmap.width * scale).toInt()
                val height = (bitmap.height * scale).toInt()

                if (y + height > pageHeight - margin) {
                    newPage()
                }

                canvas.drawBitmap(
                    bitmap,
                    null,
                    Rect(
                        margin.toInt(),
                        y.toInt(),
                        margin.toInt() + width,
                        y.toInt() + height
                    ),
                    null
                )

                y += height + 18f

            } finally {
                bitmap.recycle()
            }
        }

        document.finishPage(page)

        val output = resolver.openOutputStream(outputUri)
            ?: throw IllegalArgumentException("Cannot create PDF")

        output.use { stream ->
            document.writeTo(stream)
        }

    } finally {
        document.close()
    }

    return "Word converted to PDF successfully"
}

private fun addZipEntry(
    zip: ZipOutputStream,
    name: String,
    content: String
) {

    zip.putNextEntry(ZipEntry(name))
    zip.write(content.toByteArray(Charsets.UTF_8))
    zip.closeEntry()
}
