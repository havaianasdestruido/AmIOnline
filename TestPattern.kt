import android.util.Patterns

fun main() {
    println(Patterns.IP_ADDRESS.matcher("2606:4700:4700::1111").matches())
}
