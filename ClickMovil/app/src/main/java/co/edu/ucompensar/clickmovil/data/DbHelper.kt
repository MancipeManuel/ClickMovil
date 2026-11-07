package co.edu.ucompensar.clickmovil.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import co.edu.ucompensar.clickmovil.model.Role
import co.edu.ucompensar.clickmovil.util.Password
import java.io.File

class DbHelper private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "clickmovil.db", null, 3) {

    private val appContext = context.applicationContext

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                email TEXT NOT NULL UNIQUE,
                password_hash TEXT NOT NULL,
                phone TEXT NOT NULL,
                city TEXT NOT NULL,
                role TEXT NOT NULL,
                active INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE products (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                seller_id INTEGER NOT NULL,
                name TEXT NOT NULL,
                description TEXT NOT NULL,
                price INTEGER NOT NULL,
                category TEXT NOT NULL,
                compatibility TEXT NOT NULL,
                stock INTEGER NOT NULL,
                image_path TEXT,
                active INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE cart_items (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL,
                product_id INTEGER NOT NULL,
                quantity INTEGER NOT NULL,
                UNIQUE(user_id, product_id)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE orders (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                code TEXT NOT NULL,
                buyer_id INTEGER NOT NULL,
                total INTEGER NOT NULL,
                shipping INTEGER NOT NULL,
                status TEXT NOT NULL,
                payment_ref TEXT NOT NULL,
                city TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE order_items (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                order_id INTEGER NOT NULL,
                product_id INTEGER NOT NULL,
                seller_id INTEGER NOT NULL,
                product_name TEXT NOT NULL,
                quantity INTEGER NOT NULL,
                unit_price INTEGER NOT NULL
            )
            """.trimIndent()
        )
        seed(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS order_items")
        db.execSQL("DROP TABLE IF EXISTS orders")
        db.execSQL("DROP TABLE IF EXISTS cart_items")
        db.execSQL("DROP TABLE IF EXISTS products")
        db.execSQL("DROP TABLE IF EXISTS users")
        onCreate(db)
    }

    private fun seed(db: SQLiteDatabase) {
        val seller = insertUser(
            db, "TecnoAccesorios", "vendedor@correo.com", "123456",
            "3010000000", "Bogotá", Role.VENDEDOR
        )
        val buyer = insertUser(
            db, "Ana López", "ana@correo.com", "123456",
            "3000000000", "Bogotá", Role.COMPRADOR
        )
        insertUser(
            db, "Admin Click", "admin@correo.com", "admin123",
            "3020000000", "Bogotá", Role.ADMIN
        )
        val products = listOf(
            SeedProduct(
                "Funda silicona mate",
                "Protección contra golpes leves. Material flexible. El color puede variar según la luz de la foto.",
                32_900L, "Fundas", "Galaxy A55", 12, "funda.jpg"
            ),
            SeedProduct(
                "Cargador USB-C 20W",
                "Carga rápida de 20W. Incluye cable corto. Garantía de 30 días por defectos de fabricación.",
                49_900L, "Carga", "Universal", 8, "cargador.jpg"
            ),
            SeedProduct(
                "Audífonos Bluetooth",
                "Estuche de carga y micrófono. Compatibles con Android.",
                79_900L, "Audio", "Universal", 6, "audifonos.jpg"
            ),
            SeedProduct(
                "Batería externa 10.000 mAh",
                "Dos salidas. Carga el celular al menos dos veces.",
                89_000L, "Energía", "Universal", 5, "bateria.jpg"
            ),
            SeedProduct(
                "Cable USB-C 1 m",
                "Cable de carga y datos. Refuerzo en las puntas.",
                19_900L, "Carga", "USB-C", 20, "cable.jpg"
            ),
            SeedProduct(
                "Soporte para carro",
                "Sujeción en la rejilla del aire. Compatible con la mayoría de celulares.",
                25_900L, "Soportes", "Universal", 15, "soporte.jpg"
            ),
            SeedProduct(
                "Protector de pantalla",
                "Vidrio templado. Incluye kit de instalación.",
                18_900L, "Fundas", "Galaxy A55", 30, "protector.jpg"
            ),
            SeedProduct(
                "Adaptador USB-C a jack",
                "Salida de audio de 3.5 mm para audífonos con cable.",
                22_900L, "Adaptadores", "USB-C", 11, "adaptador.jpg"
            )
        )
        products.forEach { product ->
            insertProduct(db, seller, product)
        }
        seedSampleOrder(db, buyer, seller)
    }

    private fun seedSampleOrder(db: SQLiteDatabase, buyerId: Long, sellerId: Long) {
        val values = ContentValues().apply {
            put("code", "CM-0988")
            put("buyer_id", buyerId)
            put("total", 57_900L)
            put("shipping", 8_000L)
            put("status", "ENTREGADO")
            put("payment_ref", "PAY-22018")
            put("city", "Bogotá")
            put("created_at", System.currentTimeMillis() - 15L * 24 * 60 * 60 * 1000)
        }
        val orderId = db.insert("orders", null, values)
        val item = ContentValues().apply {
            put("order_id", orderId)
            put("product_id", 2L)
            put("seller_id", sellerId)
            put("product_name", "Cargador USB-C 20W")
            put("quantity", 1)
            put("unit_price", 49_900L)
        }
        db.insert("order_items", null, item)
    }

    private fun insertUser(
        db: SQLiteDatabase,
        name: String,
        email: String,
        password: String,
        phone: String,
        city: String,
        role: Role
    ): Long {
        val values = ContentValues().apply {
            put("name", name)
            put("email", email.lowercase())
            put("password_hash", Password.hash(password))
            put("phone", phone)
            put("city", city)
            put("role", role.name)
            put("active", 1)
        }
        return db.insert("users", null, values)
    }

    private fun insertProduct(db: SQLiteDatabase, sellerId: Long, product: SeedProduct) {
        val values = ContentValues().apply {
            put("seller_id", sellerId)
            put("name", product.name)
            put("description", product.description)
            put("price", product.price)
            put("category", product.category)
            put("compatibility", product.compatibility)
            put("stock", product.stock)
            put("image_path", copyAssetPhoto(product.assetName))
            put("active", 1)
        }
        db.insert("products", null, values)
    }

    private fun copyAssetPhoto(assetName: String): String? {
        return try {
            val dir = File(appContext.filesDir, "productos").apply { mkdirs() }
            val dest = File(dir, assetName)
            appContext.assets.open("productos/$assetName").use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            dest.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    private data class SeedProduct(
        val name: String,
        val description: String,
        val price: Long,
        val category: String,
        val compatibility: String,
        val stock: Int,
        val assetName: String
    )

    companion object {
        @Volatile
        private var instance: DbHelper? = null

        fun get(context: Context): DbHelper {
            return instance ?: synchronized(this) {
                instance ?: DbHelper(context).also { instance = it }
            }
        }
    }
}
