package io.realworld.app.web.controllers

import io.ktor.application.ApplicationCall
import io.ktor.auth.authentication
import io.ktor.request.receive
import io.ktor.response.respond
import io.realworld.app.domain.ArticleDTO
import io.realworld.app.domain.ArticlesDTO
import io.realworld.app.domain.User
import io.realworld.app.domain.exceptions.UnauthorizedException
import io.realworld.app.domain.service.ArticleService

class ArticleController(private val articleService: ArticleService) {
    // Popular slice only: list, feed, get, update, delete, and unfavorite stay stubbed.
    fun findBy(ctx: ApplicationCall): ArticlesDTO {
        return ArticlesDTO(listOf(), 0)
    }

    fun feed(ctx: ApplicationCall): ArticlesDTO {
        return ArticlesDTO(listOf(), 0)
    }

    suspend fun popular(ctx: ApplicationCall) {
        val limit = parsePageInt(ctx.request.queryParameters["limit"], 20, "limit")
        val offset = parsePageInt(ctx.request.queryParameters["offset"], 0, "offset")
        val email = ctx.authentication.principal<User>()?.email
        val (articles, count) = articleService.findPopular(limit, offset, email)
        ctx.respond(ArticlesDTO(articles, count))
    }

    fun get(ctx: ApplicationCall): ArticleDTO {
        return ArticleDTO(null)
    }

    suspend fun create(ctx: ApplicationCall) {
        val email = ctx.authentication.principal<User>()?.email
            ?: throw UnauthorizedException("User not logged.")
        val article = ctx.receive<ArticleDTO>().article
        require(
            article != null &&
                !article.title.isNullOrBlank() &&
                !article.description.isNullOrBlank() &&
                article.body.isNotBlank()
        ) { "Article is invalid." }
        ctx.respond(ArticleDTO(articleService.create(email, article)))
    }

    suspend fun update(ctx: ApplicationCall): ArticleDTO {
        ctx.parameters["slug"]
        ctx.receive<ArticleDTO>()
        return ArticleDTO(null)
    }

    fun delete(ctx: ApplicationCall) {
        ctx.parameters["slug"]
    }

    suspend fun favorite(ctx: ApplicationCall) {
        val email = ctx.authentication.principal<User>()?.email
            ?: throw UnauthorizedException("User not logged.")
        val slug = ctx.parameters["slug"] ?: throw IllegalArgumentException("Slug is required.")
        ctx.respond(ArticleDTO(articleService.favorite(email, slug)))
    }

    fun unfavorite(ctx: ApplicationCall): ArticleDTO {
        return ArticleDTO(null)
    }

    private fun parsePageInt(raw: String?, default: Int, name: String): Int {
        if (raw == null) return default
        val value = raw.toIntOrNull() ?: throw IllegalArgumentException("$name must be an integer.")
        require(value >= 0) { "$name must be greater than or equal to 0." }
        return value
    }
}
