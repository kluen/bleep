package bleep.model

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

/** Encodes legal combinations of scala versions and platform versions */
sealed trait VersionCombo {
  def asScala: Option[VersionCombo.Scala] = this match {
    case scala: VersionCombo.Scala => Some(scala)
    case _                         => None
  }
  def asJava: Option[VersionCombo.Java.type] = this match {
    case VersionCombo.Java => Some(VersionCombo.Java)
    case _                 => None
  }

  def libraries(isTest: Boolean): List[Dep] =
    this match {
      case VersionCombo.Java              => Nil
      case VersionCombo.Jvm(scalaVersion) => scalaVersion.libraries
      case VersionCombo.Js(scalaVersion, scalaJs) =>
        val testLibs = if (isTest) List(scalaJs.testInterface, scalaJs.testBridge) else Nil

        if (scalaVersion.is3) List(scalaVersion.libraries, List(scalaJs.library3, scalaVersion.scala3JsLibrary), testLibs).flatten
        else List(scalaVersion.libraries, List(scalaJs.library), testLibs).flatten

      case VersionCombo.Native(scalaVersion, scalaNative) =>
        val testLibs = if (isTest) Some(scalaNative.testInterface) else None
        val libs = if (scalaVersion.is3) scalaNative.scala3Lib else scalaNative.scalaLib
        List(libs, scalaVersion.library) ++ testLibs.toList
    }

  val compilerPlugin: Option[Dep]
  val compilerOptions: Options
}

object VersionCombo {
  implicit val codec: Codec[VersionCombo] = deriveCodec

  case object Java extends VersionCombo {
    override val compilerPlugin: Option[Dep] = None
    override val compilerOptions: Options = Options.empty
  }

  sealed trait Scala extends VersionCombo {
    def scalaVersion: VersionScala
    def asJvm: Jvm = Jvm(scalaVersion)
  }

  case class Jvm(scalaVersion: VersionScala) extends Scala {
    override val compilerPlugin: Option[Dep] = None
    override val compilerOptions: Options = Options.empty
  }

  case class Js(scalaVersion: VersionScala, scalaJsVersion: VersionScalaJs) extends Scala {
    override val compilerPlugin: Option[Dep] =
      if (scalaVersion.is3) None else Some(scalaJsVersion.compilerPlugin)

    override val compilerOptions: Options =
      if (scalaVersion.is3) Options.parse(List("-scalajs"), None)
      else Options.empty
  }

  case class Native(scalaVersion: VersionScala, scalaNative: VersionScalaNative) extends Scala {
    override val compilerPlugin: Option[Dep] =
      Some(scalaNative.compilerPlugin)

    override val compilerOptions: Options =
      Options.empty
  }

  def fromExplodedScalaAndPlatform(maybeScala: Option[VersionScala], maybePlatform: Option[Platform]): Either[String, VersionCombo] =
    maybeScala match {
      case Some(scalaVersion) =>
        maybePlatform match {
          case Some(Platform.Jvm(_)) =>
            Right(Jvm(scalaVersion))
          case Some(Platform.Js(platform)) =>
            platform.jsVersion match {
              case Some(scalaJsVersion) =>
                Right(Js(scalaVersion, scalaJsVersion))
              case None =>
                Left(s"Must specify scala.js version for scala ${scalaVersion.scalaVersion}")
            }
          case Some(Platform.Native(platform)) =>
            platform.nativeVersion match {
              case Some(scalaNativeVersion) => Right(Native(scalaVersion, scalaNativeVersion))
              case None                     => Left(s"Must specify scala native version for scala ${scalaVersion.scalaVersion}")
            }
          case _ => Left("Must specify platform")
        }

      case None =>
        maybePlatform match {
          case Some(Platform.Jvm(_)) | None => Right(Java)
          case Some(platform)               => Left(s"Must specify scala version to use platform ${platform.name}")
        }
    }

  def fromExplodedProject(p: Project): Either[String, VersionCombo] =
    fromExplodedScalaAndPlatform(p.scala.flatMap(_.version), p.platform)
}
