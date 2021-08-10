package kurenai.mybot.domain

import javax.persistence.*

@Entity
class BindingGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0L

    @Column(nullable = false)
    var qq: Long = 0L

    @Column(nullable = false)
    var tg: Long = 0L

    constructor()

    constructor(qq: Long, tg: Long) {
        this.qq = qq
        this.tg = tg
    }
}
