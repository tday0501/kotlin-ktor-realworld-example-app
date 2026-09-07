package io.realworld.app.domain.repository

import io.realworld.app.domain.Article
import io.realworld.app.domain.User
import io.realworld.app.domain.exceptions.NotFoundException
import org.jetbrains.exposed.dao.LongIdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.Date

internal object Articles : LongIdTable() {
    val slug: Column<String> = varchar("slug", 200).uniqueIndex()
    val title: Column<String> = varchar("title", 200)
    val description: Column<String> = varchar("description", 500)
    val body: Column<String> = text("body")
    val createdAt: Column<Long> = long("created_at")
    val updatedAt: Column<Long> = long("updated_at")
    val authorId: Column<Long> = long("author_id")
}

internal object Favorites : Table() {
    val user: Column<Long> = long("user").primaryKey()
    val article: Column<Long> = long("article").primaryKey()
}

class ArticleRepository {
    init {
        ensureTables()
    }

    private fun ensureTables() {
        transaction {
            SchemaUtils.create(Articles)
            SchemaUtils.create(Favorites)
        }
    }

    fun create(author: User, article: Article): Article {
        ensureTables()
        val now = System.currentTimeMillis()
        val slug = slugify(article.title ?: "")
        val id = transaction {
            Articles.insertAndGetId { row ->
                row[Articles.slug] = slug
                row[title] = article.title ?: ""
                row[description] = article.description ?: ""
                row[body] = article.body
                row[createdAt] = now
                row[updatedAt] = now
                row[authorId] = author.id!!
            }.value
        }
        return findById(id, author.id) ?: throw IllegalStateException("Article was not persisted.")
    }

    fun favorite(userId: Long, slug: String): Article {
        ensureTables()
        val articleId = findIdBySlug(slug) ?: throw NotFoundException("Article not found.")
        transaction {
            val already = Favorites.select {
                (Favorites.user eq userId) and (Favorites.article eq articleId)
            }.count() > 0
            if (!already) {
                Favorites.insert { row ->
                    row[user] = userId
                    row[article] = articleId
                }
            }
        }
        return findById(articleId, userId) ?: throw NotFoundException("Article not found.")
    }

    fun findPopular(limit: Int, offset: Int, currentUserId: Long?): Pair<List<Article>, Int> {
        ensureTables()
        return transaction {
            val total = Articles.selectAll().count()
            val favCount = Favorites.article.count()
            val rows = Articles
                .join(
                    Favorites,
                    JoinType.LEFT,
                    additionalConstraint = { Favorites.article eq Articles.id }
                )
                .slice(Articles.columns + favCount)
                .selectAll()
                .groupBy(*Articles.columns.toTypedArray())
                .orderBy(favCount to SortOrder.DESC, Articles.createdAt to SortOrder.DESC)
                .limit(limit, offset)
                .toList()
            val articles = rows.map { row ->
                toDomain(row, row[favCount].toLong(), currentUserId)
            }
            articles to total
        }
    }

    private fun findIdBySlug(slug: String): Long? = transaction {
        Articles.select { Articles.slug eq slug }
            .map { it[Articles.id].value }
            .firstOrNull()
    }

    private fun findById(id: Long, currentUserId: Long?): Article? = transaction {
        val favCount = Favorites.article.count()
        Articles
            .join(
                Favorites,
                JoinType.LEFT,
                additionalConstraint = { Favorites.article eq Articles.id }
            )
            .slice(Articles.columns + favCount)
            .select { Articles.id eq id }
            .groupBy(*Articles.columns.toTypedArray())
            .map { toDomain(it, it[favCount].toLong(), currentUserId) }
            .firstOrNull()
    }

    private fun toDomain(row: ResultRow, favoritesCount: Long, currentUserId: Long?): Article {
        val articleId = row[Articles.id].value
        val author = Users.select { Users.id eq row[Articles.authorId] }
            .map { Users.toDomain(it) }
            .first()
        val favorited = currentUserId != null && Favorites.select {
            (Favorites.article eq articleId) and (Favorites.user eq currentUserId)
        }.count() > 0
        return Article(
            slug = row[Articles.slug],
            title = row[Articles.title],
            description = row[Articles.description],
            body = row[Articles.body],
            tagList = listOf(),
            createdAt = Date(row[Articles.createdAt]),
            updatedAt = Date(row[Articles.updatedAt]),
            favorited = favorited,
            favoritesCount = favoritesCount,
            author = author.copy(password = null, token = null)
        )
    }

    companion object {
        fun slugify(title: String): String {
            return title.trim().lowercase()
                .replace(Regex("[^a-z0-9]+"), "-")
                .trim('-')
        }
    }
}
