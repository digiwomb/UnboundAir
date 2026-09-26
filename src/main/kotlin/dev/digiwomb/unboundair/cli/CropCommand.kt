package dev.digiwomb.unboundair.cli

import dev.digiwomb.unboundair.processing.CropStep
import dev.digiwomb.unboundair.processing.PageProcessor
import dev.digiwomb.unboundair.processing.PageSettings
import dev.digiwomb.unboundair.processing.pageImage
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * The `crop` command: runs only the crop step (BE-03).
 *
 * The command reads an existing JPEG file [input], runs the processing chain
 * with a single [CropStep] using default [PageSettings], and writes the
 * cropped result to [output]. No scanner is involved; the input file is never
 * modified. The step writes into a temporary work directory; [PageProcessor]
 * cleans intermediate files, leaving only the final result, which is then
 * moved to the requested output path.
 *
 * @property settings the page settings for the crop step; defaults to the
 *   default [PageSettings] (no grayscale, default plausibility thresholds).
 */
class CropCommand(
    private val settings: PageSettings = PageSettings(),
) {
    /**
     * Runs the crop step on [input] and writes the result to [output].
     *
     * @param input the source JPEG file. Must exist and be readable.
     * @param output the destination file. Parent directories are created if
     *   necessary; the file is overwritten if it already exists.
     * @return the [output] path.
     * @throws IllegalArgumentException if [input] does not exist or is not a
     *   regular file, or if the processing cannot be performed.
     */
    fun run(
        input: Path,
        output: Path,
    ): Path {
        require(Files.isRegularFile(input)) {
            "Input file does not exist or is not a regular file: $input"
        }
        val workDir = Files.createTempDirectory("unboundair-crop")
        try {
            val processor = PageProcessor(listOf(CropStep(settings)))
            val image = pageImage(input)
            val result = processor.process(image, workDir) { /* warnings ignored for CLI */ }
            Files.createDirectories(output.parent)
            if (result.file == input) {
                // The crop step changed nothing (e.g. the page fills the whole
                // frame), so the result is the input file itself: copy it, never
                // move, or the input would vanish.
                Files.copy(input, output, StandardCopyOption.REPLACE_EXISTING)
            } else {
                Files.move(result.file, output, StandardCopyOption.REPLACE_EXISTING)
            }
            return output
        } finally {
            runCatching { deleteRecursively(workDir) }
        }
    }

    private fun deleteRecursively(dir: Path) {
        if (!Files.exists(dir)) return
        Files.walk(dir).sorted(Comparator.reverseOrder()).forEach {
            runCatching { Files.deleteIfExists(it) }
        }
    }
}
