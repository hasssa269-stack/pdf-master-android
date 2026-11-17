
package com.pdfmaster.ai

import android.os.Bundle
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Navy = Color(0xFF101D35)
private val Blue = Color(0xFF3978F6)
private val Background = Color(0xFFF4F7FC)
private val DarkText = Color(0xFF17243A)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PDFMasterApp()
        }
    }
}

data class PdfTool(
    val icon: String,
    val title: String,
    val subtitle: String
)

@Composable
fun PDFMasterApp() {

    var language by remember { mutableStateOf("en") }
    var selectedFile by remember { mutableStateOf<Uri?>(null) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        selectedFile = uri
    }

    val tools = when (language) {
        "ar" -> listOf(
            PdfTool("📚", "دمج PDF", "جمع الملفات"),
            PdfTool("✂️", "تقسيم PDF", "تقسيم الصفحات"),
            PdfTool("📦", "ضغط PDF", "تقليل الحجم"),
            PdfTool("🖼️", "صور إلى PDF", "تحويل الصور"),
            PdfTool("📄", "PDF إلى صور", "استخراج الصور"),
            PdfTool("🔍", "قراءة PDF", "عرض الوثائق")
        )

        "fr" -> listOf(
            PdfTool("📚", "Fusionner PDF", "Combiner fichiers"),
            PdfTool("✂️", "Diviser PDF", "Séparer pages"),
            PdfTool("📦", "Compresser", "Réduire la taille"),
            PdfTool("🖼️", "Image vers PDF", "Convertir images"),
            PdfTool("📄", "PDF vers images", "Extraire images"),
            PdfTool("🔍", "Lire PDF", "Voir documents")
        )

        else -> listOf(
            PdfTool("📚", "Merge PDF", "Combine files"),
            PdfTool("✂️", "Split PDF", "Separate pages"),
            PdfTool("📦", "Compress PDF", "Reduce file size"),
            PdfTool("🖼️", "Images to PDF", "Convert images"),
            PdfTool("📄", "PDF to Images", "Extract images"),
            PdfTool("🔍", "Read PDF", "View documents")
        )
    }

    val title = when (language) {
        "ar" -> "أدوات PDF الخاصة بك"
        "fr" -> "Vos outils PDF"
        else -> "Your PDF tools"
    }

    val description = when (language) {
        "ar" -> "كل أدوات PDF في مكان واحد"
        "fr" -> "Tous vos outils PDF au même endroit"
        else -> "All your PDF tools in one place"
    }

    val choose = when (language) {
        "ar" -> "اختيار ملف PDF"
        "fr" -> "Choisir un fichier PDF"
        else -> "Choose PDF File"
    }

    val fileName = selectedFile?.lastPathSegment
        ?: if (language == "ar") "لم يتم اختيار ملف"
        else if (language == "fr") "Aucun fichier sélectionné"
        else "No file selected"

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Blue,
            background = Background,
            surface = Color.White
        )
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Background)
                .padding(18.dp)
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📕", fontSize = 30.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "PDF Master",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Navy
                    )
                }

                var expanded by remember { mutableStateOf(false) }

                Box {
                    Text(
                        "🌐 ${language.uppercase()}",
                        color = Blue,
                        modifier = Modifier.clickable {
                            expanded = true
                        }.padding(8.dp)
                    )

                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        listOf(
                            "en" to "English",
                            "fr" to "Français",
                            "ar" to "العربية"
                        ).forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item.second) },
                                onClick = {
                                    language = item.first
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Navy
                )
            ) {
                Column(
                    modifier = Modifier.padding(22.dp)
                ) {
                    Text(
                        title,
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        description,
                        color = Color.LightGray,
                        fontSize = 14.sp
                    )

                    Spacer(Modifier.height(18.dp))

                    Button(
                        onClick = {
                            picker.launch(arrayOf("application/pdf"))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Blue
                        )
                    ) {
                        Text(choose)
                    }

                    Text(
                        fileName,
                        color = Color.White,
                        fontSize = 12.sp,
                        maxLines = 2
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                if (language == "ar") "جميع الأدوات"
                else if (language == "fr") "Tous les outils"
                else "All Tools",
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = DarkText
            )

            Spacer(Modifier.height(12.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(tools) { tool ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(125.dp)
                            .clickable {
                                picker.launch(
                                    arrayOf("application/pdf")
                                )
                            },
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White
                        ),
                        elevation = CardDefaults.cardElevation(
                            defaultElevation = 2.dp
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(14.dp),
                            verticalArrangement =
                                Arrangement.Center
                        ) {
                            Text(
                                tool.icon,
                                fontSize = 28.sp
                            )

                            Spacer(Modifier.height(7.dp))

                            Text(
                                tool.title,
                                fontWeight = FontWeight.Bold,
                                color = Navy,
                                fontSize = 14.sp
                            )

                            Text(
                                tool.subtitle,
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            Text(
                "PDF Master AI • Version 1.0.0",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                color = Color.Gray,
                fontSize = 11.sp
            )
        }
    }
}
