package io.realworld.app.domain.service

import io.realworld.app.domain.Article
import io.realworld.app.domain.exceptions.NotFoundException
import io.realworld.app.domain.repository.ArticleRepository
import io.realworld.app.domain.repository.UserRepository

class ArticleService(
    private val articleRepository: ArticleRepository,
    private val userRepository: UserRepository
) {
    fun create(email: String, article: Article): Article {
        val author = userRepository.findByEmail(email)
            ?: throw NotFoundException("User not found.")
        return articleRepository.create(author, article)
    }

    fun favorite(email: String, slug: String): Article {
        val user = userRepository.findByEmail(email)
            ?: throw NotFoundException("User not found.")
        return articleRepository.favorite(user.id!!, slug)
    }

    fun findPopular(limit: Int, offset: Int, email: String?): Pair<List<Article>, Int> {
        val currentUserId = email?.let { userRepository.findByEmail(it)?.id }
        return articleRepository.findPopular(limit, offset, currentUserId)
    }
}
