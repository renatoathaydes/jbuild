package jbuild.commands;

import jbuild.artifact.ResolvedArtifact;
import jbuild.artifact.ResolvedArtifactChecksum;
import jbuild.util.Describable;
import jbuild.util.Either;
import jbuild.util.NonEmptyCollection;
import jbuild.util.SHA1;

import java.util.Arrays;

final class ChecksumVerifier {

    static Either<ResolvedArtifactChecksum, NonEmptyCollection<Describable>> verify(
            ResolvedArtifact artifact,
            ResolvedArtifact sha,
            boolean verbose) {
        var actual = SHA1.computeSha1(artifact.getContents());
        byte[] expected;
        try {
            expected = SHA1.fromSha1StringBytes(sha.getContents());
        } catch (IllegalArgumentException e) {
            return Either.right(NonEmptyCollection.of(Describable.of(
                    "Checksum of " + artifact.artifact.getCoordinates() + " is invalid!")));
        }
        if (!Arrays.equals(expected, actual)) {
            var suffix = verbose
                    ? " (actual=" + Arrays.toString(actual) + ", expected=" + Arrays.toString(expected) + ")"
                    : "";
            return Either.right(NonEmptyCollection.of(Describable.of(
                    "Checksum of " + artifact.artifact.getCoordinates() + " did not match!" + suffix)));
        }
        return Either.left(new ResolvedArtifactChecksum(artifact, sha));
    }
}
