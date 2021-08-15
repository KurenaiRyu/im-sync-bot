package kurenai.mybot.domain

import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id

@Entity
class BotConfig {

    @Id
    var key: String = ""
        set(value) {
            assert(value.isNotBlank())
            field = value
        }

    @Column
    var value: String = ""
        set(value) {
            assert(value.isNotBlank())
            field = value
        }

    constructor()

    constructor(key: String, value: String) {
        this.key = key
        this.value = value
        valid()
    }

    fun valid() {
        assert(key.isNotBlank())
        assert(value.isNotBlank())
    }


}