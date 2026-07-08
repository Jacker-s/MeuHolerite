$path = 'C:\Users\Jackson\StudioProjects\FCM MEU Holerite\app\src\main\java\com\jack\meuholerite\admin\MainActivity.kt'
$content = Get-Content -Path $path -Raw

$content = $content.Replace(
@'
private data class PromocaoData(
    val firestoreId: String = "",
    val titulo: String = "",
    val descricao: String = "",
    val imagemUrl: String = "",
    val precoAntes: Double = 0.0,
    val precoDepois: Double = 0.0,
    val link: String = "",
    val loja: String = "",
    val cupom: String = "",
    val verificado: Boolean = true,
    val expirada: Boolean = false,
    val curtidas: Long = 0,
    val timestamp: Double = 0.0
)
'@,
@'
private data class PromocaoData(
    val firestoreId: String = "",
    val titulo: String = "",
    val descricao: String = "",
    val imagemUrl: String = "",
    val precoAntes: Double = 0.0,
    val precoDepois: Double = 0.0,
    val link: String = "",
    val loja: String = "",
    val cupom: String = "",
    val verificado: Boolean = true,
    val expirada: Boolean = false,
    val expiraEm: Double = 0.0,
    val curtidas: Long = 0,
    val timestamp: Double = 0.0
)
'@
)

$content = $content.Replace(
    'var promoVerificado by remember { mutableStateOf(true) }',
    "var promoVerificado by remember { mutableStateOf(true) }`r`n    var promoDuracaoHoras by remember { mutableStateOf(10) }"
)

$content = $content.Replace(
@'
                    promoVerificado = promoVerificado,
                    onPromoVerificadoChange = { promoVerificado = it },
                    isSavingPromo = isSavingPromo,
'@,
@'
                    promoVerificado = promoVerificado,
                    onPromoVerificadoChange = { promoVerificado = it },
                    promoDuracaoHoras = promoDuracaoHoras,
                    onPromoDuracaoHorasChange = { promoDuracaoHoras = it },
                    isSavingPromo = isSavingPromo,
'@
)

$content = $content.Replace(
    'updatePromocao(serviceAccountJson, editingPromoId!!, promoTitulo, promoDescricao, promoImagemUrl, precoAntesVal, precoDepoisVal, promoLink, promoLoja, promoCupom, promoVerificado)',
    'updatePromocao(serviceAccountJson, editingPromoId!!, promoTitulo, promoDescricao, promoImagemUrl, precoAntesVal, precoDepoisVal, promoLink, promoLoja, promoCupom, promoVerificado, promoDuracaoHoras)'
)
$content = $content.Replace(
    'savePromocao(serviceAccountJson, promoTitulo, promoDescricao, promoImagemUrl, precoAntesVal, precoDepoisVal, promoLink, promoLoja, promoCupom, promoVerificado)',
    'savePromocao(serviceAccountJson, promoTitulo, promoDescricao, promoImagemUrl, precoAntesVal, precoDepoisVal, promoLink, promoLoja, promoCupom, promoVerificado, promoDuracaoHoras)'
)

$content = $content.Replace(
    'promoVerificado = true`r`n                                promoDuracaoHoras = 10',
    "promoVerificado = true`r`n                                promoDuracaoHoras = 10"
)
$content = $content.Replace(
    'promoVerificado = promo.verificado`r`n                        promoDuracaoHoras = (((promo.expiraEm - promo.timestamp) / 3600000.0).let { if (it.isFinite() && it > 0) kotlin.math.ceil(it).toInt() else 10 }).coerceIn(1, 168)',
    "promoVerificado = promo.verificado`r`n                        promoDuracaoHoras = (((promo.expiraEm - promo.timestamp) / 3600000.0).let { if (it.isFinite() && it > 0) kotlin.math.ceil(it).toInt() else 10 }).coerceIn(1, 168)"
)

$content = $content.Replace(
@'
                    verificado = fields.optJSONObject("verificado")?.optBoolean("booleanValue") ?: true,
                    expirada = fields.optJSONObject("expirada")?.optBoolean("booleanValue") ?: false,
                    curtidas = fields.optJSONObject("curtidas")?.optString("integerValue")?.toLongOrNull() ?: 0L,
'@,
@'
                    verificado = fields.optJSONObject("verificado")?.optBoolean("booleanValue") ?: true,
                    expirada = fields.optJSONObject("expirada")?.optBoolean("booleanValue") ?: false,
                    expiraEm = fields.optJSONObject("expiraEm")?.optDouble("doubleValue") ?: 0.0,
                    curtidas = fields.optJSONObject("curtidas")?.optString("integerValue")?.toLongOrNull() ?: 0L,
'@
)

$content = $content.Replace(
@'
private suspend fun savePromocao(
    jsonKey: String,
    titulo: String,
    descricao: String,
    imagemUrl: String,
    precoAntes: Double,
    precoDepois: Double,
    link: String,
    loja: String,
    cupom: String,
    verificado: Boolean
): String = withContext(Dispatchers.IO) {
'@,
@'
private suspend fun savePromocao(
    jsonKey: String,
    titulo: String,
    descricao: String,
    imagemUrl: String,
    precoAntes: Double,
    precoDepois: Double,
    link: String,
    loja: String,
    cupom: String,
    verificado: Boolean,
    duracaoHoras: Int
): String = withContext(Dispatchers.IO) {
'@
)

$content = $content.Replace(
    'put("expiraEm", JSONObject().put("doubleValue", (now + 5 * 60 * 60 * 1000).toDouble()))',
    'put("expiraEm", JSONObject().put("doubleValue", (now + duracaoHoras.coerceIn(1, 168) * 60 * 60 * 1000L).toDouble()))'
)

$content = $content.Replace(
@'
private suspend fun updatePromocao(
    jsonKey: String,
    promoId: String,
    titulo: String,
    descricao: String,
    imagemUrl: String,
    precoAntes: Double,
    precoDepois: Double,
    link: String,
    loja: String,
    cupom: String,
    verificado: Boolean
): String = withContext(Dispatchers.IO) {
'@,
@'
private suspend fun updatePromocao(
    jsonKey: String,
    promoId: String,
    titulo: String,
    descricao: String,
    imagemUrl: String,
    precoAntes: Double,
    precoDepois: Double,
    link: String,
    loja: String,
    cupom: String,
    verificado: Boolean,
    duracaoHoras: Int
): String = withContext(Dispatchers.IO) {
'@
)

$content = $content.Replace(
    'val fieldPaths = listOf("titulo", "descricao", "imagemUrl", "precoAntes", "precoDepois", "link", "loja", "cupom", "verificado", "expiraEm", "expirada")',
    'val fieldPaths = listOf("titulo", "descricao", "imagemUrl", "precoAntes", "precoDepois", "link", "loja", "cupom", "verificado", "expiraEm", "expirada")'
)

$content = $content.Replace(
@'
            put("cupom", JSONObject().put("stringValue", cupom))
            put("verificado", JSONObject().put("booleanValue", verificado))
        }
'@,
@'
            put("cupom", JSONObject().put("stringValue", cupom))
            put("verificado", JSONObject().put("booleanValue", verificado))
            put("expirada", JSONObject().put("booleanValue", false))
            put("expiraEm", JSONObject().put("doubleValue", (System.currentTimeMillis() + duracaoHoras.coerceIn(1, 168) * 60 * 60 * 1000L).toDouble()))
        }
'@
)

$content = $content.Replace(
@'
private fun PromocoesSection(
    promoTitulo: String,
    onPromoTituloChange: (String) -> Unit,
    promoDescricao: String,
    onPromoDescricaoChange: (String) -> Unit,
    promoImagemUrl: String,
    onPromoImagemUrlChange: (String) -> Unit,
    promoPrecoAntes: String,
    onPromoPrecoAntesChange: (String) -> Unit,
    promoPrecoDepois: String,
    onPromoPrecoDepoisChange: (String) -> Unit,
    promoLink: String,
    onPromoLinkChange: (String) -> Unit,
    promoLoja: String,
    onPromoLojaChange: (String) -> Unit,
    promoCupom: String,
    onPromoCupomChange: (String) -> Unit,
    promoVerificado: Boolean,
    onPromoVerificadoChange: (Boolean) -> Unit,
    isSavingPromo: Boolean,
    editingPromoId: String?,
    onSavePromo: () -> Unit,
    onCancelEdit: () -> Unit,
    promoList: List<PromocaoData>,
    isLoadingPromo: Boolean,
    onRefreshPromo: () -> Unit,
    onExpirePromo: (String) -> Unit,
    onEditPromo: (PromocaoData) -> Unit,
    onFetchOgImage: (String) -> Unit
)
'@,
@'
private fun PromocoesSection(
    promoTitulo: String,
    onPromoTituloChange: (String) -> Unit,
    promoDescricao: String,
    onPromoDescricaoChange: (String) -> Unit,
    promoImagemUrl: String,
    onPromoImagemUrlChange: (String) -> Unit,
    promoPrecoAntes: String,
    onPromoPrecoAntesChange: (String) -> Unit,
    promoPrecoDepois: String,
    onPromoPrecoDepoisChange: (String) -> Unit,
    promoLink: String,
    onPromoLinkChange: (String) -> Unit,
    promoLoja: String,
    onPromoLojaChange: (String) -> Unit,
    promoCupom: String,
    onPromoCupomChange: (String) -> Unit,
    promoVerificado: Boolean,
    onPromoVerificadoChange: (Boolean) -> Unit,
    promoDuracaoHoras: Int,
    onPromoDuracaoHorasChange: (Int) -> Unit,
    isSavingPromo: Boolean,
    editingPromoId: String?,
    onSavePromo: () -> Unit,
    onCancelEdit: () -> Unit,
    promoList: List<PromocaoData>,
    isLoadingPromo: Boolean,
    onRefreshPromo: () -> Unit,
    onExpirePromo: (String) -> Unit,
    onEditPromo: (PromocaoData) -> Unit,
    onFetchOgImage: (String) -> Unit
)
'@
)

if ($content -notmatch 'Tempo de exibição \(horas\)') {
    $content = $content.Replace(
@'
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
'@,
@'
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = promoDuracaoHoras.toString(),
                onValueChange = {
                    val digits = it.filter { ch -> ch.isDigit() }.take(3)
                    val value = digits.toIntOrNull() ?: 1
                    onPromoDuracaoHorasChange(value.coerceIn(1, 168))
                },
                label = { Text("Tempo de exibição (horas)") },
                supportingText = { Text("Ex.: 10h. Limite entre 1 e 168 horas.") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = null) },
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
'@
    )
}

if ($content -notmatch 'Disponível por mais') {
    $content = $content.Replace(
@'
                        if (promo.cupom.isNotEmpty()) {
'@,
@'
                        val tempoRestanteHoras = ((promo.expiraEm - System.currentTimeMillis()) / 3600000.0).let { if (it > 0) kotlin.math.ceil(it).toInt() else 0 }
                        if (tempoRestanteHoras > 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (tempoRestanteHoras == 1) "Disponível por mais 1h" else "Disponível por mais ${tempoRestanteHoras}h",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFB7791F),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (promo.cupom.isNotEmpty()) {
'@
    )
}

Set-Content -Path $path -Value $content -Encoding UTF8
