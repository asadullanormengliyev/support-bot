package uz.zero_one.supportbot

import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToMany
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import java.time.LocalDateTime
import java.util.UUID

@Entity
class UserEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(unique = true)
    var chatId: Long? = null,
    var userName: String,
    @Column(unique = true)
    var phoneNumber: String,
    var passWord: String? = null,
    @ManyToMany
    var roles: MutableList<RoleEntity> = mutableListOf(),
    @ElementCollection(targetClass = Language::class, fetch = FetchType.EAGER)
    @CollectionTable(name = "user_languages", joinColumns = [JoinColumn(name = "user_id")])
    @Enumerated(EnumType.STRING)
    @Column(name = "language")
    var languages: List<Language> = emptyList(),

    @Enumerated(EnumType.STRING)
    var selectedLanguage: Language? = null,

    var available: Boolean = true,
    @Column(nullable = false)
    var inChat: Boolean = false,
    @Enumerated(EnumType.STRING)
    var state: UserState?,
    @Column(nullable = true)
    var inChatWithId: Long? = null,
    var lastOperatorId: Long? = null,

) : UserDetails {

    override fun getAuthorities(): Collection<GrantedAuthority?>? {
        val authorities = arrayListOf<SimpleGrantedAuthority>()
        for (i in roles) {
            authorities.add(SimpleGrantedAuthority(i.authority))
        }
        return authorities
    }

    override fun getPassword(): String? {
        return passWord
    }

    override fun getUsername(): String? {
        return userName
    }

    override fun isAccountNonExpired(): Boolean = true

    override fun isAccountNonLocked(): Boolean = true

    override fun isCredentialsNonExpired(): Boolean = true

    override fun isEnabled(): Boolean = true

}

@Entity
class RoleEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long? = null,
    @Enumerated(EnumType.STRING) val role: UserRole
) : GrantedAuthority {

    override fun getAuthority(): String {
        return "ROLE_" + role.name
    }
}

@Entity
class QuestionEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne
    @JoinColumn(name = "user_chat_id")
    val user: UserEntity,

    val question: String? = null,

    var answer: String? = null,

    var isAnswered: Boolean = false,

    @ManyToOne
    @JoinColumn(name = "operator_chat_id")
    var operator: UserEntity? = null,

    @Enumerated(EnumType.STRING)
    var mediaType: MediaType? = null,

    var fileId: String? = null,

    var caption: String? = null,

    var latitude: Double? = null,
    var longitude: Double? = null,
    var createdAt: LocalDateTime = LocalDateTime.now(),
    var venueTitle: String? = null,
    var venueAddress: String? = null,
    var contactPhoneNumber: String? = null,
    var contactFirstName: String? = null,
    var contactLastName: String? = null,
    var gameTitle: String? = null,
    @ElementCollection(fetch = FetchType.EAGER)
    var pollOptions: MutableList<String> = mutableListOf(),
    var diceEmoji: String? = null,
    var invoiceTitle: String? = null,
    var sessionId: UUID? = null,
    var fromMessageId: Int? = null,
    var toMessageId: Int? = null,
)

@Entity
@Table(name = "queue_entries")
class QueueEntryEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @OneToOne
    @JoinColumn(name = "user_id", referencedColumnName = "id", unique = true)
    val user: UserEntity,
    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)

@Entity
class OperatorRatingEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @ManyToOne
    val operator: UserEntity,
    @ManyToOne
    val user: UserEntity,
    val stars: Int,
    val sessionId: UUID,
    val ratedAt: LocalDateTime = LocalDateTime.now()
)


