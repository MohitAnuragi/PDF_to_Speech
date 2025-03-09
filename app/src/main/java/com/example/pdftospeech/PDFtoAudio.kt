package com.example.pdftospeech

import android.content.Context
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.util.Log
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.InputStream
import java.util.*

@Composable
fun PdfToAudioScreen() {
    val context = LocalContext.current
    var pdfText by remember { mutableStateOf("") }
    var isSpeaking by remember { mutableStateOf(false) }
    var speechRate by remember { mutableStateOf(0.7f) }
    val ttsHelper = remember { mutableStateOf(TTSHelper(context)) }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            uri?.let {
                pdfText = extractTextFromPdf(context, it)
            }
        }
    )

    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Button(onClick = { pdfPickerLauncher.launch("application/pdf") }) {
            Text("Select PDF")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Speech Rate Slider
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Speech Speed: ${"%.1f".format(speechRate)}x", style = MaterialTheme.typography.titleMedium)
            Slider(
                value = speechRate,
                onValueChange = { newRate ->
                    speechRate = newRate
                    ttsHelper.value.setSpeechRate(speechRate)
                },
                valueRange = 0.5f..4.0f,
                steps = 6
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row {
            Button(
                onClick = {
                    if (pdfText.isNotEmpty()) {
                        isSpeaking = true
                        ttsHelper.value.speak(pdfText) {
                            isSpeaking = false
                        }
                    }
                },
                enabled = pdfText.isNotEmpty() && !isSpeaking,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Play Audio")
            }

            Spacer(modifier = Modifier.width(16.dp))

            Button(
                onClick = {
                    ttsHelper.value.stopSpeaking()
                    isSpeaking = false
                },
                enabled = isSpeaking,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Stop Audio")
            }
        }
    }
}

// Helper class for Text-to-Speech
class TTSHelper(context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech = TextToSpeech(context, this)
    private var isInitialized = false

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale("en", "IN") // 🇮🇳 Indian Accent
            isInitialized = true
            tts.setSpeechRate(1.0f)
        }
    }

    fun speak(text: String, onComplete: () -> Unit) {
        if (isInitialized) {
            val sentences = splitTextIntoChunks(text, 3900) // Split into chunks of ~4000 chars
            for (sentence in sentences) {
                tts.speak(sentence, TextToSpeech.QUEUE_ADD, null, null)
            }
        }
    }

    fun stopSpeaking() {
        if (isInitialized) {
            tts.stop()
        }
    }

    fun setSpeechRate(rate: Float) {
        if (isInitialized) {
            tts.setSpeechRate(rate)
        }
    }


    private fun splitTextIntoChunks(text: String, chunkSize: Int): List<String> {
        val sentences = text.split(Regex("(?<=\\.)")) // Split at sentence endings
        val chunks = mutableListOf<String>()
        var currentChunk = StringBuilder()

        for (sentence in sentences) {
            if (currentChunk.length + sentence.length > chunkSize) {
                chunks.add(currentChunk.toString())
                currentChunk = StringBuilder()
            }
            currentChunk.append(sentence).append(" ")
        }
        if (currentChunk.isNotEmpty()) {
            chunks.add(currentChunk.toString())
        }
        return chunks
    }
}

// Function to extract text from PDF
fun extractTextFromPdf(context: Context, uri: Uri): String {
    return try {
        val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
        val pdfReader = PdfReader(inputStream)
        val pdfDocument = PdfDocument(pdfReader)

        val extractedText = StringBuilder()
        for (page in 1..pdfDocument.numberOfPages) {
            extractedText.append(PdfTextExtractor.getTextFromPage(pdfDocument.getPage(page)))
            extractedText.append("\n\n") // Separate pages with newlines
        }

        pdfDocument.close()
        pdfReader.close()
        inputStream?.close()

        val text = extractedText.toString().trim()
        if (text.isEmpty()) "No text found in the PDF" else text
    } catch (e: Exception) {
        Log.e("PDFExtraction", "Error extracting text: ${e.message}")
        "Failed to extract text"
    }
}
