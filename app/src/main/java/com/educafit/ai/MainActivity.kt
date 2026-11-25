package com.educafit.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.tensai.llamakt.ChatMessage
import com.tensai.llamakt.LlamaEngine
import com.tensai.llamakt.SamplingParams
import com.tensai.llamakt.chat
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MODEL_ASSET = "educafit-qwen2.5-0.5b-q4km.gguf"
private const val MODEL_MIN_BYTES = 300L * 1024L * 1024L

private val SYSTEM_PROMPT = """
Você é o EducaFit AI PRO, um assistente de aprendizagem especializado em Educação Física escolar. Responda em português brasileiro, com linguagem clara, correta e adequada a estudantes.

Seu foco inclui esportes, regras, fundamentos, jogos e brincadeiras, anatomia e fisiologia básicas aplicadas ao movimento, capacidades motoras, treinamento físico educativo, saúde e segurança na prática corporal, planejamento de aulas, avaliação escolar, história e cultura corporal, dança, lutas e ginásticas.

Ensine passo a passo quando necessário. Use exemplos simples, tabelas, resumos, perguntas de revisão e planos de aula quando forem pedidos. Não invente fontes, leis, citações ou dados. Quando não souber, admita a incerteza.

Se a pergunta não tiver relação com Educação Física, explique com educação que seu foco é essa disciplina. Não diagnostique doenças nem prescreva tratamentos, medicamentos, dietas ou exercícios extremos. Não incentive comparação corporal, perda de peso rápida, excesso de treino ou práticas perigosas. Em caso de dúvida médica, recomende conversar com um responsável e um profissional de saúde. Priorize segurança, orientação de professor, aquecimento adequado, hidratação, pausas e adaptação à idade.

Você funciona sem internet e não deve afirmar que pesquisou na internet. Você pode cometer erros; recomende conferir informações importantes com o professor e os materiais escolares.
""".trimIndent()

private data class UiMessage(val role: String, val content: String)

class MainActivity : ComponentActivity() {
    private val messages = mutableStateListOf<UiMessage>()
    private var engine: LlamaEngine? = null
    private var modelReady by mutableStateOf(false)
    private var loading by mutableStateOf(true)
    private var busy by mutableStateOf(false)
    private var status by mutableStateOf("Preparando a IA incluída no aplicativo…")
    private var loadProgress by mutableStateOf(0f)
    private var generationJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(11, 18, 32)
        window.navigationBarColor = android.graphics.Color.rgb(11, 18, 32)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFF27D3B2),
                onPrimary = Color(0xFF07131C),
                background = Color(0xFF0B1220),
                surface = Color(0xFF121D2D),
                onSurface = Color(0xFFEAF2F8),
                onBackground = Color(0xFFEAF2F8)
            )) {
                EducaFitScreen(
                    messages = messages,
                    modelReady = modelReady,
                    loading = loading,
                    busy = busy,
                    status = status,
                    loadProgress = loadProgress,
                    onSend = ::sendMessage,
                    onNewChat = ::newConversation
                )
            }
        }
        prepareLocalModel()
    }

    private fun prepareLocalModel() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val modelFile = File(filesDir, MODEL_ASSET)
                if (!modelFile.exists() || modelFile.length() < MODEL_MIN_BYTES) {
                    withContext(Dispatchers.Main) {
                        status = "Copiando o modelo que já veio no APK…"
                        loadProgress = 0.02f
                    }
                    val tempFile = File(filesDir, "$MODEL_ASSET.tmp")
                    assets.open(MODEL_ASSET).buffered().use { input ->
                        FileOutputStream(tempFile).buffered().use { output ->
                            val buffer = ByteArray(256 * 1024)
                            var copied = 0L
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                                copied += count
                                // The asset is several hundred MB; show a safe coarse progress estimate.
                                if (copied % (32L * 1024L * 1024L) < buffer.size) {
                                    withContext(Dispatchers.Main) {
                                        loadProgress = (0.02f + (copied.toFloat() / (420f * 1024f * 1024f)) * 0.30f).coerceIn(0.02f, 0.32f)
                                        status = "Copiando o modelo local… ${copied / (1024L * 1024L)} MB"
                                    }
                                }
                            }
                            output.flush()
                        }
                    }
                    if (modelFile.exists()) modelFile.delete()
                    check(tempFile.renameTo(modelFile)) { "Não foi possível salvar o modelo no armazenamento do aplicativo." }
                }

                withContext(Dispatchers.Main) {
                    status = "Carregando a IA no processador do celular…"
                    loadProgress = 0.42f
                }

                val localEngine = LlamaEngine()
                localEngine.load(
                    path = modelFile.absolutePath,
                    nGpuLayers = 0,
                    nCtx = 2048,
                    nThreads = 0
                )

                withContext(Dispatchers.Main) {
                    engine = localEngine
                    modelReady = true
                    loading = false
                    loadProgress = 1f
                    status = "IA local pronta · sem internet"
                }
            } catch (error: Exception) {
                withContext(Dispatchers.Main) {
                    loading = false
                    modelReady = false
                    status = "Não foi possível iniciar a IA: ${error.message ?: "erro desconhecido"}"
                }
            }
        }
    }

    private fun sendMessage(rawText: String) {
        val text = rawText.trim()
        val activeEngine = engine ?: return
        if (text.isEmpty() || busy) return

        messages.add(UiMessage("user", text))
        messages.add(UiMessage("assistant", ""))
        val responseIndex = messages.lastIndex
        val history = buildList {
            add(ChatMessage("system", SYSTEM_PROMPT))
            messages.take(responseIndex).takeLast(12).forEach { item ->
                add(ChatMessage(item.role, item.content))
            }
        }
        busy = true

        generationJob = lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    activeEngine.chat(
                        history,
                        SamplingParams(
                            nPredict = 320,
                            temperature = 0.65f,
                            topP = 0.90f,
                            repeatPenalty = 1.08f
                        ),
                        enableThinking = false
                    ).collect { token ->
                        withContext(Dispatchers.Main) {
                            val current = messages[responseIndex]
                            messages[responseIndex] = current.copy(content = current.content + token)
                        }
                    }
                }
            } catch (error: Exception) {
                val current = messages.getOrNull(responseIndex)
                if (current != null && current.content.isBlank()) {
                    messages[responseIndex] = current.copy(
                        content = "Não consegui gerar a resposta. ${error.message ?: "Tente novamente."}"
                    )
                }
            } finally {
                busy = false
            }
        }
    }

    private fun newConversation() {
        if (busy) return
        messages.clear()
    }

    override fun onDestroy() {
        generationJob?.cancel()
        engine?.free()
        engine = null
        super.onDestroy()
    }
}

@Composable
private fun EducaFitScreen(
    messages: List<UiMessage>,
    modelReady: Boolean,
    loading: Boolean,
    busy: Boolean,
    status: String,
    loadProgress: Float,
    onSend: (String) -> Unit,
    onNewChat: () -> Unit
) {
    var draft by androidx.compose.runtime.remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, messages.lastOrNull()?.content) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B1220))
            .imePadding()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = RoundedCornerShape(15.dp), color = Color(0xFF153B43), modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text("EF", color = Color(0xFF56E4C5), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("EducaFit AI", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF2F7FA))
                Text("PRO · Educação Física", fontSize = 12.sp, color = Color(0xFF9BAFC0))
            }
            Surface(shape = CircleShape, color = if (modelReady) Color(0xFF123B34) else Color(0xFF3B3020)) {
                Text(
                    if (modelReady) "● OFFLINE" else "● IA",
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                    color = if (modelReady) Color(0xFF6DE2C6) else Color(0xFFFFD18B),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Divider(color = Color(0xFF243244))
        Spacer(Modifier.height(12.dp))

        if (messages.isEmpty()) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("APRENDIZAGEM INTELIGENTE", color = Color(0xFF6DE2C6), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Text("Aprenda.\nMovimente-se. Evolua.", color = Color(0xFFF3F7FA), fontSize = 30.sp, lineHeight = 37.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Uma IA generativa de Educação Física que executa no próprio celular, sem enviar suas perguntas para a nuvem.",
                    color = Color(0xFF9BAFC0), fontSize = 14.sp, lineHeight = 21.sp
                )
                Spacer(Modifier.height(22.dp))
                listOf(
                    "🏐  Regras e fundamentos dos esportes",
                    "🧠  Anatomia e capacidades motoras",
                    "📚  Resumos, revisões e questionários",
                    "🏃  Planos de aula e atividades seguras"
                ).forEach { item ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        color = Color(0xFF121D2D),
                        shape = RoundedCornerShape(13.dp)
                    ) {
                        Text(item, modifier = Modifier.padding(13.dp), color = Color(0xFFDCE7EE), fontSize = 13.sp)
                    }
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(messages) { _, item ->
                    MessageBubble(item)
                }
            }
        }

        if (loading || !modelReady) {
            Surface(color = Color(0xFF121D2D), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                Column(Modifier.padding(13.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color(0xFF27D3B2))
                        Spacer(Modifier.width(10.dp))
                        Text(status, color = Color(0xFFC2D1DC), fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(9.dp))
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { loadProgress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF27D3B2),
                        trackColor = Color(0xFF263447)
                    )
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                Box(Modifier.size(7.dp).background(Color(0xFF27D3B2), CircleShape))
                Spacer(Modifier.width(7.dp))
                Text(status, color = Color(0xFF83A99F), fontSize = 11.sp)
                Spacer(Modifier.weight(1f))
                Text("CPU local", color = Color(0xFF71869A), fontSize = 10.sp)
                Spacer(Modifier.width(12.dp))
                androidx.compose.material3.TextButton(onClick = onNewChat, enabled = !busy) {
                    Text("Nova conversa", color = Color(0xFF75DCC5), fontSize = 11.sp)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                enabled = modelReady && !busy,
                placeholder = { Text(if (modelReady) "Pergunte sobre Educação Física…" else "Aguardando a IA local…", color = Color(0xFF8193A5), fontSize = 13.sp) },
                shape = RoundedCornerShape(17.dp),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (draft.isNotBlank() && modelReady && !busy) {
                        onSend(draft)
                        draft = ""
                    }
                }),
                colors = androidx.compose.material3.TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF121D2D),
                    unfocusedContainerColor = Color(0xFF121D2D),
                    disabledContainerColor = Color(0xFF101927),
                    focusedTextColor = Color(0xFFEAF2F8),
                    unfocusedTextColor = Color(0xFFEAF2F8),
                    cursorColor = Color(0xFF27D3B2),
                    focusedIndicatorColor = Color(0xFF27D3B2),
                    unfocusedIndicatorColor = Color(0xFF344356)
                )
            )
            Spacer(Modifier.width(9.dp))
            Button(
                onClick = {
                    if (draft.isNotBlank()) {
                        onSend(draft)
                        draft = ""
                    }
                },
                enabled = modelReady && !busy && draft.isNotBlank(),
                modifier = Modifier.height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27D3B2), disabledContainerColor = Color(0xFF344356))
            ) {
                Text("➤", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
        Text(
            "Modelo local pequeno: pode errar. Confirme conteúdos importantes com seu professor.",
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            color = Color(0xFF71869A),
            fontSize = 10.sp
        )
    }
}

@Composable
private fun MessageBubble(message: UiMessage) {
    val isUser = message.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (isUser) Color(0xFF164D4A) else Color(0xFF172436),
            shape = RoundedCornerShape(17.dp),
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            Column(Modifier.padding(13.dp)) {
                Text(
                    if (isUser) "VOCÊ" else "EDUCAFIT AI",
                    color = if (isUser) Color(0xFF91E7D2) else Color(0xFF68DCC0),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    message.content.ifBlank { "Pensando…" },
                    color = Color(0xFFEAF2F8),
                    fontSize = 14.sp,
                    lineHeight = 21.sp
                )
            }
        }
    }
}
