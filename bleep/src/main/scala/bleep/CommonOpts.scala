package bleep

import com.monovore.decline.Opts
import cats.syntax.apply._

case class CommonOpts(noColor: Boolean, debug: Boolean, directory: Option[String])

object CommonOpts {
  val noColor: Opts[Boolean] = Opts.flag("no-color", "enable CI-friendly output").orFalse
  val debug: Opts[Boolean] = Opts.flag("debug", "enable more output").orFalse
  val directory: Opts[Option[String]] = Opts.option[String]("directory", "enable more output", "d").orNone

  val opts: Opts[CommonOpts] =
    (noColor, debug, directory).mapN { case (noColor, debug, directory) =>
      CommonOpts(noColor = noColor, debug = debug, directory)
    }

  // we apparently need to parse (and remove from input) before the rest of the app is launched
  def parse(args: List[String]): (CommonOpts, List[String]) = {
    var noColor = false
    var debug = false
    var directory = Option.empty[String]
    val keepArgs = List.newBuilder[String]
    var idx = 0
    while (idx < args.length) {
      args(idx) match {
        case "--no-color" => noColor = true
        case "--debug"    => debug = true
        case "-d" | "--directory" if args.isDefinedAt(idx + 1) =>
          directory = Some(args(idx + 1))
          idx += 1
        case "--" =>
          keepArgs ++= args.drop(idx)
          idx = Int.MaxValue - 1
        case other => keepArgs += other
      }
      idx += 1
    }

    (CommonOpts(noColor, debug, directory), keepArgs.result())
  }
}
