package com.jack.meuholerite.database

import android.content.Context
import androidx.room.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jack.meuholerite.model.EspelhoItem
import com.jack.meuholerite.model.EspelhoPonto
import com.jack.meuholerite.model.ReciboItem
import com.jack.meuholerite.model.ReciboPagamento
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "espelhos", indices = [Index(value = ["periodo"], unique = true)])
data class EspelhoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val funcionario: String,
    val periodo: String,
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
    val empresa: String,
    val proventosJson: String,
    val descontosJson: String,
    val totalProventos: String,
    val totalDescontos: String,
    val valorLiquido: String,
    val baseInss: String,
    val fgtsMes: String,
    val baseIrpf: String,
    val timestamp: Long = System.currentTimeMillis(),
    val pdfFilePath: String? = null
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
}

@Database(entities = [EspelhoEntity::class, ReciboEntity::class], version = 7)
abstract class AppDatabase : RoomDatabase() {
    abstract fun espelhoDao(): EspelhoDao
    abstract fun reciboDao(): ReciboDao

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
        periodo = this.periodo,
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
        periodo = this.periodo,
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
        empresa = this.empresa,
        proventosJson = gson.toJson(this.proventos),
        descontosJson = gson.toJson(this.descontos),
        totalProventos = this.totalProventos,
        totalDescontos = this.totalDescontos,
        valorLiquido = this.valorLiquido,
        baseInss = this.baseInss,
        fgtsMes = this.fgtsMes,
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
        empresa = this.empresa,
        proventos = gson.fromJson(this.proventosJson, itemType),
        descontos = gson.fromJson(this.descontosJson, itemType),
        totalProventos = this.totalProventos,
        totalDescontos = this.totalDescontos,
        valorLiquido = this.valorLiquido,
        baseInss = this.baseInss,
        fgtsMes = this.fgtsMes,
        baseIrpf = this.baseIrpf,
        pdfFilePath = this.pdfFilePath
    )
}
