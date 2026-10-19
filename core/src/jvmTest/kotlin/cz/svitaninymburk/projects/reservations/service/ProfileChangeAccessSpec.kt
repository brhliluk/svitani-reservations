package cz.svitaninymburk.projects.reservations.service

import cz.svitaninymburk.projects.reservations.auth.HashingService
import cz.svitaninymburk.projects.reservations.error.UserError
import cz.svitaninymburk.projects.reservations.repository.user.InMemoryUserRepository
import cz.svitaninymburk.projects.reservations.user.User
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Profilové změny se smí dotknout jen účtu volajícího.
 *
 * Dřív braly `userId` parametrem a nic ho neověřovalo, takže kterýkoli přihlášený
 * uživatel přepsal jméno nebo e-mail libovolného účtu. U e-mailu to bylo obzvlášť
 * zlé: rezervace bez účtu se k účtu párují právě podle adresy.
 */
class ProfileChangeAccessSpec {

    private val callerId: Uuid = Uuid.parse("00000000-0000-0000-0000-0000000000c1")
    private val victimId: Uuid = Uuid.parse("00000000-0000-0000-0000-0000000000c2")

    private val userRepo = InMemoryUserRepository()

    private object NoopHashing : HashingService {
        override fun generateSaltedHash(value: String): String = value
        override fun verify(value: String, saltedHash: String): Boolean = value == saltedHash
    }

    private inner class TestService(private val caller: Uuid?) : UserService(userRepo, NoopHashing) {
        override suspend fun currentUserId(): Uuid? = caller
    }

    private suspend fun givenUser(id: Uuid, email: String) {
        userRepo.create(
            User.Email(
                id = id, email = email, name = "Jan", surname = "Uživatel",
                role = User.Role.USER, passwordHash = "x",
            )
        )
    }

    @Test
    fun `email change applies to the account of the caller`() = runBlocking {
        givenUser(callerId, "volajici@test.cz")
        givenUser(victimId, "obet@test.cz")

        val result = TestService(callerId).changeEmail("novy@test.cz")

        assertTrue(result.isRight(), "mělo projít, dostal: $result")
        assertEquals("novy@test.cz", userRepo.findById(callerId)?.email)
        assertEquals("obet@test.cz", userRepo.findById(victimId)?.email)
        Unit
    }

    @Test
    fun `unauthenticated caller changes nothing`() = runBlocking {
        givenUser(victimId, "obet@test.cz")

        val result = TestService(caller = null).changeEmail("utocnik@test.cz")

        assertTrue(result.leftOrNull() is UserError.UserNotFound)
        assertEquals("obet@test.cz", userRepo.findById(victimId)?.email)
        Unit
    }

    @Test
    fun `name and surname change also applies only to the caller`() = runBlocking {
        givenUser(callerId, "volajici@test.cz")
        givenUser(victimId, "obet@test.cz")

        TestService(callerId).changeName("Nové")
        TestService(callerId).changeSurname("Jméno")

        assertEquals("Nové", userRepo.findById(callerId)?.name)
        assertEquals("Jméno", userRepo.findById(callerId)?.surname)
        assertEquals("Jan", userRepo.findById(victimId)?.name)
        Unit
    }
}
