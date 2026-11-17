package com.pdfmaster.ai

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
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
import kotlin.math.min

private enum class PdfAction {
    NONE, READ, MERGE, SPLIT, COMPRESS, IMAGES
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

    var status by remember { mutableStateOf("اختار الخدمة اللي بغيتي") }
    var busy by remember { mutableStateOf(false) }

    val isArabic = language == "ar"

    fun runTask(task: () -> String) {
        if (busy) return

        busy = true
        status = if (isArabic) "جاري المعالجة..." else "Processing..."

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
            val result = renderPdfPage(resolver, uri, pageIndex)

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

    val outputPicker =
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
                            mergePdfs(resolver, uris, outputUri)
                        }

                        PdfAction.SPLIT -> {
                            val first = firstPage
                                ?: throw IllegalArgumentException(
                                    "Invalid first page number"
                                )
                            val last = lastPage
                                ?: throw IllegalArgumentException(
                                    "Invalid last page number"
                                )

                            if (uris.isEmpty()) {
                                throw IllegalArgumentException("No PDF selected")
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
                                throw IllegalArgumentException("No PDF selected")
                            }

                            compressPdf(
                                resolver,
                                uris.first(),
                                outputUri
                            )
                        }

                        PdfAction.IMAGES -> {
                            imagesToPdf(resolver, uris, outputUri)
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
                        outputPicker.launch("split.pdf")
                    }

                    PdfAction.COMPRESS -> {
                        selectedUris = listOf(uri)
                        outputPicker.launch("compressed.pdf")
                    }

                    else -> Unit
                }
            }
        }

    val multiPdfPicker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenMultipleDocuments()
        ) { uris ->
            if (uris.isNotEmpty()) {
                selectedUris = uris
                outputPicker.launch("merged.pdf")
            }
        }

    val imagePicker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenMultipleDocuments()
        ) { uris ->
            if (uris.isNotEmpty()) {
                selectedUris = uris
                outputPicker.launch("images.pdf")
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
                                Text(if (isArabic) "من الصفحة" else "From page")
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
                                Text(if (isArabic) "إلى الصفحة" else "To page")
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

                                if (first == null || last == null ||
                                    first < 1 || last < first
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
                    "fr" -> "Réduire la taille avec perte possible de qualité"
                    else -> "Reduce size with possible quality loss"
                },
                enabled = !busy
            ) {
                action = PdfAction.COMPRESS
                pdfPicker.launch(arrayOf("application/pdf"))
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF3A2B1C)
                )
            ) {
                Text(
                    text = if (isArabic) {
                        "تنبيه: الضغط يحوّل صفحات PDF إلى صور. قد تنقص الجودة، وتضيع إمكانية البحث والنسخ والروابط، كما أن الحجم النهائي ليس مضموناً أن يكون أصغر."
                    } else {
                        "Warning: compression converts pages to images. Quality may decrease, searchable text and links are lost, and the output is not guaranteed to be smaller."
                    },
                    color = Color.White,
                    modifier = Modifier.padding(12.dp),
                    fontSize = 13.sp
                )
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
                            val targetPage = currentPage - 1
                            if (uri != null) {
                                openReaderPage(uri, targetPage)
                            }
                        }
                    ) {
                        Text(if (isArabic) "السابق" else "Previous")
                    }

                    Button(
                        enabled = !busy && currentPage < pageCount - 1,
                        onClick = {
                            val uri = readerUri
                            val targetPage = currentPage + 1
                            if (uri != null) {
                                openReaderPage(uri, targetPage)
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
