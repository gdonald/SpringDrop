package dev.springdrop.kernel.jsonapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class JsonApiQueryTest {

    private static JsonApiQuery parse(String... pairs) {
        Map<String, String[]> parameters = new LinkedHashMap<>();
        for (int place = 0; place < pairs.length; place += 2) {
            String name = pairs[place];
            String[] existing = parameters.get(name);
            if (existing == null) {
                parameters.put(name, new String[] {pairs[place + 1]});
            } else {
                String[] grown = java.util.Arrays.copyOf(existing, existing.length + 1);
                grown[existing.length] = pairs[place + 1];
                parameters.put(name, grown);
            }
        }
        return JsonApiQuery.parse(parameters);
    }

    private static void refused(String detail, String... pairs) {
        assertThatThrownBy(() -> parse(pairs)).isInstanceOfSatisfying(JsonApiException.class, refusal -> {
            assertThat(refusal.status()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(refusal.getMessage()).isEqualTo(detail);
        });
    }

    private static List<JsonApiFilter> rootMembers(JsonApiQuery query) {
        return ((JsonApiFilter.Group) query.filter()).members();
    }

    @Nested
    class Defaults {

        @Test
        void aRequestWithoutParametersAsksForTheFirstFullPageOfEverything() {
            JsonApiQuery query = parse();

            assertThat(query.filter()).isNull();
            assertThat(query.sorts()).isEmpty();
            assertThat(query.offset()).isZero();
            assertThat(query.limit()).isEqualTo(JsonApiQuery.MAX_LIMIT);
            assertThat(query.fieldsOf("node--article")).isNull();
            assertThat(query.includes()).isEmpty();
        }
    }

    @Nested
    class Filters {

        @Test
        void aShorthandFilterComparesThePathForEquality() {
            assertThat(rootMembers(parse("filter[label]", "Spring schedule")))
                    .containsExactly(new JsonApiFilter.Match("label", "=", "Spring schedule"));
        }

        @Test
        void aShortcutFilterNamesItsOperatorBesideItsValue() {
            assertThat(rootMembers(parse("filter[label][value]", "Spring", "filter[label][operator]", "contains")))
                    .containsExactly(new JsonApiFilter.Match("label", "CONTAINS", "Spring"));
        }

        @Test
        void aShortcutFilterWithTheInOperatorTakesAListOfValues() {
            assertThat(rootMembers(parse("filter[id][operator]", "IN", "filter[id][value][]", "1",
                    "filter[id][value][]", "2")))
                    .containsExactly(new JsonApiFilter.Match("id", "IN", List.of("1", "2")));
        }

        @Test
        void aNamedConditionGivesItsPathOperatorAndValue() {
            assertThat(rootMembers(parse("filter[cheap][condition][path]", "price",
                    "filter[cheap][condition][operator]", "<", "filter[cheap][condition][value]", "10")))
                    .containsExactly(new JsonApiFilter.Match("price", "<", "10"));
        }

        @Test
        void conditionsJoinNamedGroupsThatNestInsideEachOther() {
            JsonApiQuery query = parse(
                    "filter[either][group][conjunction]", "or",
                    "filter[inner][group][conjunction]", "AND",
                    "filter[inner][group][memberOf]", "either",
                    "filter[toys][condition][path]", "category",
                    "filter[toys][condition][value]", "toys",
                    "filter[toys][condition][memberOf]", "either",
                    "filter[music][condition][path]", "category",
                    "filter[music][condition][value]", "music",
                    "filter[music][condition][memberOf]", "inner");

            assertThat(rootMembers(query)).containsExactly(new JsonApiFilter.Group("OR", List.of(
                    new JsonApiFilter.Group("AND", List.of(new JsonApiFilter.Match("category", "=", "music"))),
                    new JsonApiFilter.Match("category", "=", "toys"))));
        }

        @Test
        void aParameterOutsideTheFilterSyntaxIsRefused() {
            refused("The filter parameter filter[a][b] is not understood.", "filter[a][b]", "x");
            refused("The filter parameter filter[a][condition] is not understood.", "filter[a][condition]", "x");
            refused("The filter parameter filter[a][condition][color] is not understood.",
                    "filter[a][condition][color]", "x");
            refused("The filter parameter filter[a][condition][path][] is not understood.",
                    "filter[a][condition][path][]", "x");
            refused("The filter parameter filter[a][group][color] is not understood.",
                    "filter[a][group][color]", "x");
            refused("The filter parameter filter[a][group] is not understood.", "filter[a][group]", "x");
            refused("The filter parameter filter[a][b][c] is not understood.", "filter[a][b][c]", "x");
        }

        @Test
        void aMemberOfSomethingThatIsNotAGroupIsRefused() {
            refused("The filter a is a member of b, which is not a group.",
                    "filter[a][condition][path]", "x", "filter[a][condition][value]", "1",
                    "filter[a][condition][memberOf]", "b");
            refused("The filter a is a member of b, which is not a group.",
                    "filter[a][condition][path]", "x", "filter[a][condition][value]", "1",
                    "filter[a][condition][memberOf]", "b", "filter[b]", "2");
        }

        @Test
        void groupsThatAreMembersOfEachOtherAreRefused() {
            refused("The filter groups a, b are members of each other.",
                    "filter[a][group][conjunction]", "AND", "filter[a][group][memberOf]", "b",
                    "filter[b][group][conjunction]", "AND", "filter[b][group][memberOf]", "a", "filter[ok]", "1");
        }

        @Test
        void aGroupWithoutAConjunctionOfAndOrOrIsRefused() {
            refused("The filter group a needs a conjunction of AND or OR.", "filter[a][group][memberOf]", "b",
                    "filter[b][group][conjunction]", "OR");
            refused("The filter group a needs a conjunction of AND or OR.", "filter[a][group][conjunction]", "XOR");
        }

        @Test
        void aConditionNeedsAPathAKnownOperatorAndAValue() {
            refused("The filter a has no path.", "filter[a][condition][value]", "1");
            refused("The filter a has no path.", "filter[a][condition][path]", " ", "filter[a][condition][value]",
                    "1");
            refused("The filter operator LIKE is not supported. Use one of =, <>, >, <, IN, CONTAINS.",
                    "filter[a][operator]", "LIKE", "filter[a][value]", "1");
            refused("The filter a has no value.", "filter[a][condition][path]", "x");
        }

        @Test
        void anOperatorOtherThanInTakesOneValue() {
            refused("The filter a takes one value with the = operator.", "filter[a][value][]", "1");
            refused("The filter a takes one value with the = operator.", "filter[a]", "1", "filter[a]", "2");
        }
    }

    @Nested
    class SortsPagesFieldsAndIncludes {

        @Test
        void aSortNamesPathsInOrderWithAMinusForDescending() {
            assertThat(parse("sort", "-created, label").sorts()).containsExactly(
                    new JsonApiQuery.SortField("created", false), new JsonApiQuery.SortField("label", true));
        }

        @Test
        void aSortWithAnEmptyPathIsRefused() {
            refused("The sort parameter label,,id names an empty path.", "sort", "label,,id");
            refused("The sort parameter - names an empty path.", "sort", "-");
        }

        @Test
        void aPageGivesItsOffsetAndALimitOfAtMostFifty() {
            JsonApiQuery query = parse("page[offset]", "10", "page[limit]", "500");

            assertThat(query.offset()).isEqualTo(10);
            assertThat(query.limit()).isEqualTo(JsonApiQuery.MAX_LIMIT);
            assertThat(parse("page[limit]", "2").limit()).isEqualTo(2);
        }

        @Test
        void aPageThatIsNotAWholeNumberOrHasNoRoomIsRefused() {
            refused("The page[offset] parameter is not a whole number of zero or more.", "page[offset]", "-1");
            refused("The page[limit] parameter is not a whole number of zero or more.", "page[limit]", "ten");
            refused("The page[limit] parameter is at least 1.", "page[limit]", "0");
        }

        @Test
        void sparseFieldsetsNameTheFieldsOfEachResourceType() {
            JsonApiQuery query = parse("fields[node--article]", "label, tags,", "fields[user--user]", "");

            assertThat(query.fieldsOf("node--article")).isEqualTo(Set.of("label", "tags"));
            assertThat(query.fieldsOf("user--user")).isEmpty();
            assertThat(query.fieldsOf("taxonomy_term--tags")).isNull();
        }

        @Test
        void includesAreDottedRelationshipPaths() {
            assertThat(parse("include", "tags, owner.picture").includes())
                    .containsExactly(List.of("tags"), List.of("owner", "picture"));
        }

        @Test
        void anIncludeWithAnEmptyPathIsRefused() {
            refused("The include parameter tags. names an empty path.", "include", "tags.");
            refused("The include parameter tags,,owner names an empty path.", "include", "tags,,owner");
        }

        @Test
        void otherParametersAreLeftAlone() {
            assertThat(parse("_format", "json").filter()).isNull();
        }
    }
}
