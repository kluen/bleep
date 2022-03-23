package bleep

import bleep.commands.BuildCreateNew
import bleep.logging.LogLevel
import cats.data.NonEmptyList

import java.nio.file.{Path, Paths}

class CreateNewSnapshotTests extends SnapshotTest {
  val logger = logging.stdout(LogPatterns.logFile).untyped.filter(LogLevel.info)
  val outFolder = Paths.get("snapshot-tests").toAbsolutePath

  test("create-new-build") {
    val buildPaths = BuildPaths.fromBuildDir(_cwd = Path.of("/tmp"), outFolder / "create-new-build", BuildPaths.Mode.Normal)

    val generatedProjectFiles: Map[Path, String] =
      BuildCreateNew(
        logger,
        cwd = buildPaths.buildDir,
        platforms = NonEmptyList.of(model.PlatformId.Jvm, model.PlatformId.Js),
        scalas = NonEmptyList.of(Versions.Scala3, Versions.Scala213),
        name = "myapp"
      ).genAllFiles(buildPaths)

    val Right(started) = bootstrap.from(Prebootstrapped(buildPaths, logger), GenBloopFiles.InMemory, Nil)

    val generatedBloopFiles: Map[Path, String] =
      GenBloopFiles.encodedFiles(buildPaths, started.bloopFiles).map { case (path, s) => (path, absolutePaths.templatize.string(s)) }

    val allGeneratedFiles = generatedProjectFiles ++ generatedBloopFiles

    // flush templated bloop files to disk if local, compare to checked in if test is running in CI
    // note, keep last. locally it "succeeds" with a `pending`
    writeAndCompare(buildPaths.buildDir, allGeneratedFiles)
  }
}
