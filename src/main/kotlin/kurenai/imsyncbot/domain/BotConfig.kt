package kurenai.imsyncbot.domain

import kurenai.imsyncbot.BotConfigKey
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

    constructor(key: BotConfigKey, value: String) {
        this.key = key.value
        this.value = value
        valid()
    }

    constructor(key: BotConfigKey, value: Any) {
        this.key = key.value
        this.value = value.toString()
        valid()
    }

    fun valid() {
        assert(key.isNotBlank())
        assert(value.isNotBlank())
    }


}