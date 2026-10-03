package dev.springdrop.kernel.comment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class CommentThreadCodeTest {

    @Test
    void aNumberIsCodedAsItsBase36DigitsLedByTheirCountLessOne() {
        assertThat(CommentService.toVancode(0)).isEqualTo("00");
        assertThat(CommentService.toVancode(35)).isEqualTo("0z");
        assertThat(CommentService.toVancode(36)).isEqualTo("110");
    }

    @Test
    void codesSortTheWayTheirNumbersDo() {
        assertThat(CommentService.toVancode(35)).isLessThan(CommentService.toVancode(36));
        assertThat(CommentService.toVancode(9)).isLessThan(CommentService.toVancode(10));
    }

    @Test
    void aCodeReadsBackAsItsNumber() {
        assertThat(CommentService.fromVancode(CommentService.toVancode(1_000))).isEqualTo(1_000);
    }

    @Test
    void aThreadPositionsDepthIsHowManyLevelsItGoesDown() {
        assertThat(CommentService.depthOf("00/")).isZero();
        assertThat(CommentService.depthOf("00.01.00/")).isEqualTo(2);
    }

    @Test
    void settingsLeftOutOrWrittenAsTextAreRead() {
        CommentSettings settings = CommentSettings.of(Map.of(CommentSettings.DEPTH, "3",
                CommentSettings.DEFAULT_MODE, CommentSettings.FLAT));

        assertThat(settings).isEqualTo(new CommentSettings(CommentSettings.FLAT, CommentSettings.CONTACT_NONE,
                CommentSettings.PREVIEW_OPTIONAL, 3));
        assertThat(settings.allowsReplyAt(0)).isFalse();
    }

    @Test
    void aThreadMayGoAsDeepAsItsDepthAllows() {
        CommentSettings twoLevels = new CommentSettings(CommentSettings.THREADED, 0, 0, 2);
        CommentSettings anyDepth = new CommentSettings(CommentSettings.THREADED, 0, 0, 0);

        assertThat(twoLevels.allowsReplyAt(0)).isTrue();
        assertThat(twoLevels.allowsReplyAt(1)).isFalse();
        assertThat(anyDepth.allowsReplyAt(40)).isTrue();
    }

    @Test
    void aSubjectMadeFromTextEndsAtTheLastWholeWordThatFits() {
        assertThat(CommentService.subjectFrom("The opening hours change in May, so plan ahead."))
                .isEqualTo("The opening hours change in");
        assertThat(CommentService.subjectFrom("The opening hours change in a week."))
                .isEqualTo("The opening hours change in a");
        assertThat(CommentService.subjectFrom("Supercalifragilisticexpialidocious!"))
                .isEqualTo("Supercalifragilisticexpialido");
    }

    @Test
    void theCommentPluginsNameThemselvesAndTheFieldStoresAStatus() {
        CommentFieldType type = new CommentFieldType();

        assertThat(type.id()).isEqualTo(CommentFieldType.ID);
        assertThat(type.properties()).hasSize(1);
        assertThat(type.defaultStorageSettings()).isEmpty();
        assertThat(type.defaultInstanceSettings()).isEqualTo(CommentSettings.DEFAULTS);
        assertThat(new CommentStatusWidget().id()).isEqualTo(CommentStatusWidget.ID);
    }

    @Test
    void aStoredStatusOutsideTheThreeIsHidden() {
        assertThat(CommentStatus.of(2)).isEqualTo(CommentStatus.OPEN);
        assertThat(CommentStatus.of("1")).isEqualTo(CommentStatus.CLOSED);
        assertThat(CommentStatus.of(9)).isEqualTo(CommentStatus.HIDDEN);
        assertThat(CommentStatus.CLOSED.label()).isEqualTo("Closed");
    }
}
