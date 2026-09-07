package io.realworld.app.web.controllers

import io.realworld.app.domain.Article
import io.realworld.app.domain.ArticleDTO
import io.realworld.app.domain.ArticlesDTO
import io.realworld.app.web.rules.AppRule
import io.realworld.app.web.util.HttpUtil
import org.apache.http.HttpStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ArticlePopularControllerTest {
    @Rule
    @JvmField
    val appRule = AppRule()

    @Test
    fun `popular feed is empty when no articles exist`() {
        val response = HttpUtil(appRule.port).get<ArticlesDTO>("/api/articles/feed/popular")

        assertEquals(HttpStatus.SC_OK, response.status)
        assertTrue(response.body.articles.isEmpty())
        assertEquals(0, response.body.articlesCount)
    }

    @Test
    fun `popular feed is available without authentication`() {
        val author = signedIn("author_public@valid_email.com", "author_public")
        createArticle(author, "Public popular article")

        val response = HttpUtil(appRule.port).get<ArticlesDTO>("/api/articles/feed/popular")

        assertEquals(HttpStatus.SC_OK, response.status)
        assertEquals(1, response.body.articlesCount)
        assertEquals("public-popular-article", response.body.articles.first().slug)
    }

    @Test
    fun `popular feed orders articles by favorites descending`() {
        val author = signedIn("author_order@valid_email.com", "author_order")
        val twoFavs = createArticle(author, "Two favorites")
        val oneFav = createArticle(author, "One favorite")
        createArticle(author, "Zero favorites")

        favoriteAs("fan_a@valid_email.com", "fan_a", twoFavs.slug!!)
        favoriteAs("fan_b@valid_email.com", "fan_b", twoFavs.slug!!)
        favoriteAs("fan_c@valid_email.com", "fan_c", oneFav.slug!!)

        val response = author.get<ArticlesDTO>("/api/articles/feed/popular")

        assertEquals(HttpStatus.SC_OK, response.status)
        assertEquals(3, response.body.articlesCount)
        assertEquals(listOf("two-favorites", "one-favorite", "zero-favorites"), response.body.articles.map { it.slug })
        assertEquals(listOf(2L, 1L, 0L), response.body.articles.map { it.favoritesCount })
    }

    @Test
    fun `popular feed paginates with limit and offset and reports total count`() {
        val author = signedIn("author_page@valid_email.com", "author_page")
        val first = createArticle(author, "Most popular")
        val second = createArticle(author, "Second popular")
        createArticle(author, "Least popular")

        favoriteAs("page_a@valid_email.com", "page_a", first.slug!!)
        favoriteAs("page_b@valid_email.com", "page_b", first.slug!!)
        favoriteAs("page_c@valid_email.com", "page_c", second.slug!!)

        val response = HttpUtil(appRule.port).get<ArticlesDTO>(
            "/api/articles/feed/popular",
            mapOf("limit" to 1, "offset" to 1)
        )

        assertEquals(HttpStatus.SC_OK, response.status)
        assertEquals(3, response.body.articlesCount)
        assertEquals(1, response.body.articles.size)
        assertEquals("second-popular", response.body.articles.first().slug)
    }

    @Test
    fun `popular feed breaks ties with newer createdAt first`() {
        val author = signedIn("author_tie@valid_email.com", "author_tie")
        createArticle(author, "Older tied article")
        Thread.sleep(10)
        createArticle(author, "Newer tied article")

        val response = HttpUtil(appRule.port).get<ArticlesDTO>("/api/articles/feed/popular")

        assertEquals(HttpStatus.SC_OK, response.status)
        assertEquals(2, response.body.articlesCount)
        assertEquals(listOf("newer-tied-article", "older-tied-article"), response.body.articles.map { it.slug })
    }

    private fun signedIn(email: String, username: String): HttpUtil {
        val http = HttpUtil(appRule.port)
        http.registerUser(email, "password", username)
        http.loginAndSetTokenHeader(email, "password")
        return http
    }

    private fun createArticle(http: HttpUtil, title: String): Article {
        val response = http.post<ArticleDTO>(
            "/api/articles",
            ArticleDTO(Article(title = title, description = "desc", body = "body"))
        )
        assertEquals(HttpStatus.SC_OK, response.status)
        return response.body.article!!
    }

    private fun favoriteAs(email: String, username: String, slug: String) {
        val fan = signedIn(email, username)
        val response = fan.post<ArticleDTO>("/api/articles/$slug/favorite")
        assertEquals(HttpStatus.SC_OK, response.status)
    }
}
