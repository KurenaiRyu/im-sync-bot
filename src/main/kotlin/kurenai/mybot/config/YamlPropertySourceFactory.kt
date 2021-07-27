//package kurenai.mybot.config
//
//import org.springframework.boot.env.YamlPropertySourceLoader
//import org.springframework.core.env.PropertySource
//import org.springframework.core.io.Resource
//import org.springframework.core.io.support.EncodedResource
//import org.springframework.core.io.support.PropertySourceFactory
//import org.springframework.util.StringUtils
//import java.io.IOException
//
//class YamlPropertySourceFactory : PropertySourceFactory {
//
//    @Throws(IOException::class)
//    override fun createPropertySource(name: String?, resource: EncodedResource): PropertySource<*> {
//        return if (name != null)
//            YamlPropertySourceLoader().load(name, resource.resource)[0]
//        else
//            YamlPropertySourceLoader().load(
//                getNameForResource(resource.resource), resource.resource)[0]
//    }
//
//    private fun getNameForResource(resource: Resource): String {
//        var name = resource.description
//        if (!StringUtils.hasText(name)) {
//            name = resource::class.java.simpleName + "@" + System.identityHashCode(resource)
//        }
//        return name
//    }
//}