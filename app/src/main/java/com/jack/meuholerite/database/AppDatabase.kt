package com.jack.meuholerite.database

import android.content.Context
import androidx.room.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jack.meuholerite.model.EspelhoItem
import com.jack.meuholerite.model.EspelhoPonto
import com.jack.meuholerite.model.ReciboItem
import com.jack.meuholerite.model.ReciboPagamento
import com.jack.meuholerite.model.InformeRendimento
import kotlinx.coroutines.flow.Flow

// Nova entidade para rastrear PDFs escaneados
@Entity(tableName = "scanned_pdfs", indices = [Index(value = ["filePath"], unique = true)])
data class PdfDocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val filePath: String,
    val isProcessed: Boolean = false, // True se o PDF já foi parseado e os dados inseridos
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface PdfDocumentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(pdf: PdfDocumentEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM scanned_pdfs WHERE filePath = :filePath LIMIT 1)")
    suspend fun exists(filePath: String): Boolean

    @Query("SELECT * FROM scanned_pdfs WHERE isProcessed = 0")
    suspend fun getUnprocessedPdfs(): List<PdfDocumentEntity>

    @Query("UPDATE scanned_pdfs SET isProcessed = 1 WHERE filePath = :filePath")
    suspend fun markAsProcessed(filePath: String)
}

@Entity(tableName = "espelhos", indices = [Index(value = ["periodo"], unique = true)])
data class EspelhoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val funcionario: String,
    val matricula: String = "",
    val cargo: String = "",
    val empresa: String = "",
    val periodo: String,
    val jornada: String = "",
    val jornadaRealizada: String = "",
    val resumoItensJson: String,
    val saldoFinalBH: String,
    val saldoPeriodoBH: String = "0:00",
    val detalhesSaldoBH: String,
    val hasAbsences: Boolean = false,
    val diasFaltasJson: String = "[]",
    val timestamp: Long = System.currentTimeMillis(),
    val pdfFilePath: String? = null
)

@Entity(tableName = "recibos", indices = [Index(value = ["periodo"], unique = true)])
data class ReciboEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val funcionario: String,
    val matricula: String = "",
    val periodo: String,
    val dataPagamento: String = "",
    val dataAdmissao: String = "",
    val empresa: String,
    val proventosJson: String,
    val descontosJson: String,
    val totalProventos: String,
    val totalDescontos: String,
    val valorLiquido: String,
    val cargo: String = "",
    val salarioBase: String = "",
    val baseInss: String,
    val fgtsMes: String,
    val valorFgts: String = "0,00",
    val baseIrpf: String,
    val timestamp: Long = System.currentTimeMillis(),
    val pdfFilePath: String? = null
)

@Entity(tableName = "informes", indices = [Index(value = ["anoCalendario"], unique = true)])
data class InformeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val anoCalendario: String,
    val exercicio: String = "",
    val cnpjFontePagadora: String = "",
    val nomeFontePagadora: String = "",
    val cpfBeneficiario: String = "",
    val nomeBeneficiario: String = "",
    val rendimentosTributaveis: String = "0,00",
    val previdenciaOficial: String = "0,00",
    val impostoRetido: String = "0,00",
    val decimoTerceiro: String = "0,00",
    val impostoDecimoTerceiro: String = "0,00",
    val plr: String = "0,00",
    val timestamp: Long = System.currentTimeMillis(),
    val pdfFilePath: String? = null
)

@Entity(tableName = "finance_expenses", indices = [Index(value = ["description", "value", "timestamp"], unique = true)])
data class FinanceExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val description: String,
    val value: Double,
    val category: String = "OTHERS",
    val isFixed: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "finance_goals")
data class FinanceGoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val targetAmount: Double,
    val currentAmount: Double,
    val colorHex: String = "#10B981"
)

@Entity(tableName = "finance_debts")
data class FinanceDebtEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val description: String,
    val totalAmount: Double,
    val remainingAmount: Double,
    val totalInstallments: Int,
    val paidInstallments: Int,
    val monthlyValue: Double,
    val interestRate: Double = 0.0, // Monthly interest rate
    val dueDate: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface EspelhoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(espelho: EspelhoEntity)

    @Query("SELECT * FROM espelhos ORDER BY timestamp DESC")
    suspend fun getAll(): List<EspelhoEntity>

    @Query("SELECT * FROM espelhos ORDER BY timestamp DESC")
    fun getAllFlow(): Flow<List<EspelhoEntity>>

    @Query("DELETE FROM espelhos WHERE periodo = :periodo")
    suspend fun deleteByPeriodo(periodo: String)

    @Delete
    suspend fun delete(espelho: EspelhoEntity)

    @Query("DELETE FROM espelhos")
    suspend fun deleteAll()

    @Query("SELECT EXISTS(SELECT 1 FROM espelhos WHERE periodo = :periodo LIMIT 1)")
    suspend fun exists(periodo: String): Boolean
}

@Dao
interface ReciboDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(recibo: ReciboEntity)

    @Query("SELECT * FROM recibos ORDER BY timestamp DESC")
    suspend fun getAll(): List<ReciboEntity>

    @Query("SELECT * FROM recibos ORDER BY timestamp DESC")
    fun getAllFlow(): Flow<List<ReciboEntity>>

    @Query("DELETE FROM recibos WHERE periodo = :periodo")
    suspend fun deleteByPeriodo(periodo: String)

    @Delete
    suspend fun delete(recibo: ReciboEntity)

    @Query("DELETE FROM recibos")
    suspend fun deleteAll()

    @Query("SELECT EXISTS(SELECT 1 FROM recibos WHERE periodo = :periodo LIMIT 1)")
    suspend fun exists(periodo: String): Boolean
}

@Dao
interface InformeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(informe: InformeEntity)

    @Query("SELECT * FROM informes ORDER BY anoCalendario DESC")
    suspend fun getAll(): List<InformeEntity>

    @Query("SELECT * FROM informes ORDER BY anoCalendario DESC")
    fun getAllFlow(): Flow<List<InformeEntity>>

    @Query("DELETE FROM informes WHERE anoCalendario = :anoCalendario")
    suspend fun deleteByAno(anoCalendario: String)

    @Delete
    suspend fun delete(informe: InformeEntity)

    @Query("DELETE FROM informes")
    suspend fun deleteAll()

    @Query("SELECT EXISTS(SELECT 1 FROM informes WHERE anoCalendario = :anoCalendario LIMIT 1)")
    suspend fun exists(anoCalendario: String): Boolean
}

@Dao
interface FinanceExpenseDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(expense: FinanceExpenseEntity)

    @Query("SELECT * FROM finance_expenses ORDER BY timestamp DESC")
    fun getAllFlow(): Flow<List<FinanceExpenseEntity>>

    @Query("SELECT * FROM finance_expenses")
    suspend fun getAll(): List<FinanceExpenseEntity>

    @Delete
    suspend fun delete(expense: FinanceExpenseEntity)

    @Query("DELETE FROM finance_expenses")
    suspend fun deleteAll()
}

@Dao
interface FinanceGoalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(goal: FinanceGoalEntity)

    @Query("SELECT * FROM finance_goals ORDER BY id DESC")
    fun getAllFlow(): Flow<List<FinanceGoalEntity>>

    @Query("SELECT * FROM finance_goals")
    suspend fun getAll(): List<FinanceGoalEntity>

    @Delete
    suspend fun delete(goal: FinanceGoalEntity)

    @Update
    suspend fun update(goal: FinanceGoalEntity)

    @Query("DELETE FROM finance_goals")
    suspend fun deleteAll()
}

@Dao
interface FinanceDebtDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(debt: FinanceDebtEntity)

    @Query("SELECT * FROM finance_debts ORDER BY timestamp DESC")
    fun getAllFlow(): Flow<List<FinanceDebtEntity>>

    @Query("SELECT * FROM finance_debts")
    suspend fun getAll(): List<FinanceDebtEntity>

    @Update
    suspend fun update(debt: FinanceDebtEntity)

    @Delete
    suspend fun delete(debt: FinanceDebtEntity)

    @Query("DELETE FROM finance_debts")
    suspend fun deleteAll()
}

@Database(entities = [EspelhoEntity::class, ReciboEntity::class, InformeEntity::class, FinanceExpenseEntity::class, PdfDocumentEntity::class, FinanceGoalEntity::class, FinanceDebtEntity::class], version = 21)
abstract class AppDatabase : RoomDatabase() {
    abstract fun espelhoDao(): EspelhoDao
    abstract fun reciboDao(): ReciboDao
    abstract fun informeDao(): InformeDao
    abstract fun financeExpenseDao(): FinanceExpenseDao
    abstract fun financeGoalDao(): FinanceGoalDao
    abstract fun financeDebtDao(): FinanceDebtDao
    abstract fun pdfDocumentDao(): PdfDocumentDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "meu_holerite_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// Helpers for Espelho
fun EspelhoPonto.toEntity(gson: Gson, pdfPath: String? = null): EspelhoEntity {
    return EspelhoEntity(
        funcionario = this.funcionario,
        matricula = this.matricula,
        cargo = this.cargo,
        empresa = this.empresa,
        periodo = this.periodo,
        jornada = this.jornada,
        jornadaRealizada = this.jornadaRealizada,
        resumoItensJson = gson.toJson(this.resumoItens),
        saldoFinalBH = this.saldoFinalBH,
        saldoPeriodoBH = this.saldoPeriodoBH,
        detalhesSaldoBH = this.detalhesSaldoBH,
        hasAbsences = this.hasAbsences,
        diasFaltasJson = gson.toJson(this.diasFaltas),
        pdfFilePath = pdfPath
    )
}

fun EspelhoEntity.toModel(gson: Gson): EspelhoPonto {
    val itemType = object : TypeToken<List<EspelhoItem>>() {}.type
    val listType = object : TypeToken<List<String>>() {}.type
    return EspelhoPonto(
        funcionario = this.funcionario,
        matricula = this.matricula,
        cargo = this.cargo,
        empresa = this.empresa,
        periodo = this.periodo,
        jornada = this.jornada,
        jornadaRealizada = this.jornadaRealizada,
        resumoItens = gson.fromJson(this.resumoItensJson, itemType),
        saldoFinalBH = this.saldoFinalBH,
        saldoPeriodoBH = this.saldoPeriodoBH,
        detalhesSaldoBH = this.detalhesSaldoBH,
        hasAbsences = this.hasAbsences,
        diasFaltas = gson.fromJson(this.diasFaltasJson, listType) ?: emptyList(),
        pdfFilePath = this.pdfFilePath
    )
}

// Helpers for Recibo
fun ReciboPagamento.toEntity(gson: Gson, pdfPath: String? = null): ReciboEntity {
    return ReciboEntity(
        funcionario = this.funcionario,
        matricula = this.matricula,
        periodo = this.periodo,
        dataPagamento = this.dataPagamento,
        dataAdmissao = this.dataAdmissao,
        empresa = this.empresa,
        proventosJson = gson.toJson(this.proventos),
        descontosJson = gson.toJson(this.descontos),
        totalProventos = this.totalProventos,
        totalDescontos = this.totalDescontos,
        valorLiquido = this.valorLiquido,
        cargo = this.cargo,
        salarioBase = this.salarioBase,
        baseInss = this.baseInss,
        fgtsMes = this.fgtsMes,
        valorFgts = this.valorFgts,
        baseIrpf = this.baseIrpf,
        pdfFilePath = pdfPath
    )
}

fun ReciboEntity.toModel(gson: Gson): ReciboPagamento {
    val itemType = object : TypeToken<List<ReciboItem>>() {}.type
    return ReciboPagamento(
        funcionario = this.funcionario,
        matricula = this.matricula,
        periodo = this.periodo,
        dataPagamento = this.dataPagamento,
        dataAdmissao = this.dataAdmissao,
        empresa = this.empresa,
        proventos = gson.fromJson(this.proventosJson, itemType),
        descontos = gson.fromJson(this.descontosJson, itemType),
        totalProventos = this.totalProventos,
        totalDescontos = this.totalDescontos,
        valorLiquido = this.valorLiquido,
        cargo = this.cargo,
        salarioBase = this.salarioBase,
        baseInss = this.baseInss,
        fgtsMes = this.fgtsMes,
        valorFgts = this.valorFgts,
        baseIrpf = this.baseIrpf,
        pdfFilePath = this.pdfFilePath
    )
}

// Helpers for Informe
fun InformeRendimento.toEntity(pdfPath: String? = null): InformeEntity {
    return InformeEntity(
        anoCalendario = this.anoCalendario,
        exercicio = this.exercicio,
        cnpjFontePagadora = this.cnpjFontePagadora,
        nomeFontePagadora = this.nomeFontePagadora,
        cpfBeneficiario = this.cpfBeneficiario,
        nomeBeneficiario = this.nomeBeneficiario,
        rendimentosTributaveis = this.rendimentosTributaveis,
        previdenciaOficial = this.previdenciaOficial,
        impostoRetido = this.impostoRetido,
        decimoTerceiro = this.decimoTerceiro,
        impostoDecimoTerceiro = this.impostoDecimoTerceiro,
        plr = this.plr,
        pdfFilePath = pdfPath ?: this.pdfFilePath
    )
}

fun InformeEntity.toModel(): InformeRendimento {
    return InformeRendimento(
        anoCalendario = this.anoCalendario,
        exercicio = this.exercicio,
        cnpjFontePagadora = this.cnpjFontePagadora,
        nomeFontePagadora = this.nomeFontePagadora,
        cpfBeneficiario = this.cpfBeneficiario,
        nomeBeneficiario = this.nomeBeneficiario,
        rendimentosTributaveis = this.rendimentosTributaveis,
        previdenciaOficial = this.previdenciaOficial,
        impostoRetido = this.impostoRetido,
        decimoTerceiro = this.decimoTerceiro,
        impostoDecimoTerceiro = this.impostoDecimoTerceiro,
        plr = this.plr,
        pdfFilePath = this.pdfFilePath
    )
}
