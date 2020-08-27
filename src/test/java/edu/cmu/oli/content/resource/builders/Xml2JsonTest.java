package edu.cmu.oli.content.resource.builders;

import org.junit.Test;
import static org.junit.Assert.assertTrue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.google.gson.JsonElement;
import edu.cmu.oli.content.contentfiles.utils.ResourceTestPipeline;

/**
 * 
 */
public class Xml2JsonTest {
  @Test
  public void shouldHandleParagraphAndCodeblockNewlines() throws Exception {

    final ResourceTestPipeline pipeline = new ResourceTestPipeline(Xml2JsonTest.class.getResource("test-resources/example-quiz-assessment2.xml"),
        "x-oli-assessment2") {


      public JsonElement afterToJson(final JsonElement e) {
        return e;
      }
      
      public String afterDocToString(final String str) {
        // check newlines removed in paragraph elements:
        assertTrue(str.contains("<p>1 2 3</p>"));
        assertTrue(str.contains("<p>4 5 6</p>"));

          System.out.println(str);
        // check newlines retained in preformatted codeblocks
        Matcher regex = Pattern.compile(
          "<codeblock syntax\\=\"xml\"><\\!\\[CDATA\\[7$\\s+8$\\s+9\\]\\]></codeblock>",
          Pattern.DOTALL | Pattern.MULTILINE
        ).matcher(str);
        assertTrue("XML contains 7\\n        8\\n        9", regex.find());


        regex = Pattern.compile(
          "<codeblock syntax\\=\"xml\"><\\!\\[CDATA\\[$\\s+10$\\s+11$\\s+12\\]\\]></codeblock>",
          Pattern.DOTALL | Pattern.MULTILINE
        ).matcher(str);
        assertTrue("XML contains \\n        10\\n        11\\n        12", regex.find());
        
        return str;
      }

    };

    pipeline.execute();
  }

  @Test
  public void shouldHandleLargeMathMLResource() throws Exception {

    final ResourceTestPipeline pipeline = new ResourceTestPipeline(Xml2JsonTest.class.getResource("test-resources/allformulas-sort-uniq.xml"),
        "x-oli-workbook_page") {

        public JsonElement afterToJson(final JsonElement e) {
            return e;
        }

        public String afterDocToString(final String str) {

        // check newlines removed in paragraph elements:
        assertTrue(str.contains("<p><youtube id=\"gandam\" src=\"9bZkp7q19f0\" /></p>"));

        assertTrue(str.contains("<p><youtube id=\"corning\" src=\"6Cf7IL_eZ38\" controls=\"false\" /></p>"));

        assertTrue(str.contains(
          "<p>1. <formula style=\"inline\">$$4x-\\frac{1}{3}x^3_{-2}^2$$</formula> 2. <formula style=\"inline\">"
          + "$$4x-x^3_{-2}^2$$</formula> 3. <formula style=\"inline\">$4-\\frac{1}{3}x^3_{-2}^2$</formula> 4. "
          + "<formula style=\"inline\">$4x-\\frac{1}{3}x^3_{0}^9$</formula></p>"
        ));

        assertTrue(str.contains(
          "<formula><m:math overflow=\"scroll\"><m:mfrac><m:mrow><m:mi>n</m:mi><m:mo>!</m:mo></m:mrow><m:mrow>"
          + "<m:mi>x</m:mi><m:mo>!</m:mo><m:mo>(</m:mo><m:mi>n</m:mi><m:mo>-</m:mo><m:mi>x</m:mi><m:mo>)</m:mo>"
          + "<m:mo>!</m:mo></m:mrow></m:mfrac></m:math></formula>"
        ));

        return str;
      }

    };

    pipeline.execute();
  }

  @Test
  public void shouldHandleMixedElementTypesBiology() throws Exception {

    final ResourceTestPipeline pipeline = new ResourceTestPipeline(Xml2JsonTest.class.getResource("test-resources/kinetics2_productB.xml"),
        "x-oli-workbook_page") {

        public JsonElement afterToJson(final JsonElement e) {
            return e;
        }

        public String afterDocToString(final String str) {

        // check newlines removed in paragraph elements:
        assertTrue(str.contains("<objective id=\"obj29\">Give the general reaction for an enzyme catalyzed reaction.</objective>"));

        assertTrue(str.contains("<objective id=\"obj32\">Effect of inhibitors: Be able to explain, and graphically " +
                "illustrate, the effect of each inhibitor type on a velocity vs substrate " +
                "concentration graph: a) non-competitive and b) competitive. </objective>"));

        assertTrue(str.contains("<p>" +
                "<formula>E + S &lt;=&gt; [ES] -&gt; P + E </formula>" +
                "</p>"));

        assertTrue(str.contains("<p>In this equation, k<sub>cat</sub> is the rate at which the [ES] complex decays " +
                "to product. V<sub>max</sub> is k<sub>cat</sub> multiplied by the <em>total</em> " +
                "amount of enzyme, E<sub>t</sub>. It is therefore the maximum velocity at which " +
                "the reaction can occur under a fixed enzyme concentration. K<sub>m</sub> is also " +
                "a constant that reflects the affinity of the enzyme for the substrate and is an " +
                "approximation of the dissociation equilibrium constant. Both k<sub>cat</sub> and " +
                "K<sub>m</sub> depend on the particular enzyme and substrate.</p>"));

        assertTrue(str.contains("<p>This equation can also be represented graphically by the hyperbolic curve " +
                "illustrated below. This curve can be dissected into two parts: a region at the " +
                "beginning where the velocity of the reaction increases proportionately with the " +
                "amount of substrate present initially in the reaction, and the later region of " +
                "the curve, at high substrate concentration, where the rate of the reaction is " +
                "independent of the substrate concentration. This latter region defines " +
                "saturation of the enzyme by substrate and the velocity approaches the value of " +
                "V<sub>max</sub>.</p>" +
                "<image src=\"../../../webcontent/amino_acids/mm_ekin.jpg\">" +
                "<caption>Effect of substrate concentration on enzyme velocity.</caption>" +
                "</image>"));

        assertTrue(str.contains("<wb:inline idref=\"inline_digit_Enzymes_2\" purpose=\"didigetthis\" />"));

        return str;
      }

    };

    pipeline.execute();
  }

  @Test
  public void shouldHandleObjectives() throws Exception {

    final ResourceTestPipeline pipeline = new ResourceTestPipeline(Xml2JsonTest.class.getResource("test-resources/_u2_m1_objectives.xml"),
        "x-oli-learning_objectives") {

        public JsonElement afterToJson(final JsonElement e) {
            return e;
        }

        public String afterDocToString(final String str) {

        // check newlines removed in paragraph elements:
        assertTrue(str.contains(
            "<!DOCTYPE objectives PUBLIC \"-//Carnegie Mellon University//DTD Learning Objectives" +
            " 2.0//EN\" \"http://oli.web.cmu.edu/dtd/oli_learning_objectives_2_0.dtd\">" +
            "<objectives id=\"_u2_m1_objectives\">" +
            "<title>Couples</title>" +
            "<objective id=\"recognize_forces_rotation\" category=\"domain_specific\">Recognize that at least " +
            "two forces are necessary to cause only a " +
            "rotation of a body, and utilize the concept of a couple and its symbol to " +
            "represent combinations of forces that produce zero net force (only a tendency to " +
            "rotate). </objective>" +
            "<objective id=\"determine_moment_couple\" category=\"domain_specific\">Determine the moment of the " +
                "couple from the forces producing it or " +
            "balancing it, and recognize that a set of forces that can be represented as couples " +
            "produces the same net moment about all points regardless of " +
            "where they are applied. </objective>" +
            "<objective id=\"recognize_multi-force_interaction\" category=\"domain_specific\">Recognize circumstances" +
                " in which a multi-force interaction between " +
            "two bodies can be represented as a couple, and determine the moment of the couple " +
            "from what the couple interaction is balancing. </objective></objectives>"));

        return str;
      }

    };

    pipeline.execute();
  }

  @Test
  public void shouldHandleInlineAssessment() throws Exception {

    final ResourceTestPipeline pipeline = new ResourceTestPipeline(Xml2JsonTest.class.getResource("test-resources/_u02_m01_privs_DIGT_8c.xml"),
        "x-oli-inline-assessment") {

        public JsonElement afterToJson(final JsonElement e) {
            return e;
        }

        public String afterDocToString(final String str) {

        // check newlines removed in paragraph elements:
        assertTrue(str.contains("<content>" +
                 "<p>More test assd As soon as he got onto the campus network, Kurt began scanning for shared files, " +
                 "hoping to find free music and movies. In the process of looking for media, he found " +
                 "that he also had access to some students’ personal files—health histories, journals, " +
                 "even some pictures. Since the information wasn’t protected, Kurt felt free to enjoy " +
                 "the free movies, songs and applications, and started snooping on other students’ " +
                 "information. Kurt didn’t stop at accessing the files himself; he also began to share " +
                 "many of the files with his friends, and to talk about some of the pictures he viewed " +
                 "with a number of his close friends. </p>" +
                 "<p>Which of Kurt’s actions are acceptable according to the Computing Policy and " +
                 "guidelines?</p>" +
                 "<table>" +
                 "<tr>" +
                 "<td colspan=\"100%\">1</td>" +
                 "</tr>" +
                 "<tr>" +
                 "<td colspan=\"100%\">2</td>" +
                 "</tr>" +
                 "<tr>" +
                 "<td colspan=\"100%\">3</td>" +
                 "</tr>" +
                 "</table>" +
                 "</content>"));

        assertTrue(str.contains("<question id=\"_u02_m01_privs_DIGT_8c_5\">" +
                "<body><p>Here is a <term>Term Tag</term>, and a " +
                "<extra><anchor>process</anchor><meaning>" +
                "<material><p>This is the meaning.</p></material>" +
                "</meaning></extra>" +
                "</p></body>" +
                "<multiple_choice shuffle=\"false\" id=\"ans\">" +
                "<choice value=\"y\">Acceptable</choice>" +
                "<choice value=\"n\">Unacceptable</choice>" +
                "</multiple_choice>" +
                "<part>" +
                "<response match=\"y\" score=\"0\">" +
                "<feedback>Incorrect. The Computing Policy explicitly states that one should " +
                "assume files are intended to be private.</feedback></response>" +
                "<response match=\"n\" score=\"1\">" +
                "<feedback>Correct. Although the files may not have been appropriately secured, " +
                "Kurt still needs to be respectful of other users’ privacy.</feedback>" +
                "</response>" +
                "<hint>Unless the names of the files, folders or directories were clearly marked as " +
                "public, Kurt must ask for permission to access them. </hint>" +
                "</part>" +
                "</question>"));

        return str;
      }

    };

    pipeline.execute();
  }

  @Test
  public void shouldHandleSummativeAssessment() throws Exception {

    final ResourceTestPipeline pipeline = new ResourceTestPipeline(Xml2JsonTest.class.getResource("test-resources/_u02_m0_pre-assm.xml"),
        "x-oli-assessment2") {

        public String afterDocToString(final String str) {

        // check newlines removed in paragraph elements:
        assertTrue(str.contains("<fill_in_the_blank id=\"SA_u02_m01_mixed_objectives\">" +
                "<title>Assess Yourself</title>" +
                "<body><p><em>Assess Yourself</em></p>" +
                "<p>How well can you summarize your privileges and responsibilities as a member of " +
                "the Carnegie Mellon community, describe the range of penalties for misuse of " +
                "computing resources and recognize inappropriate behavior with regard to the use " +
                "of computing resources?</p>" +
                "<p><input_ref input=\"choice1\" /></p></body>" +
                "<input shuffle=\"false\" id=\"choice1\">" +
                "<choice value=\"v1\">I cannot do this at all.</choice>" +
                "<choice value=\"v2\">I can do this with some support.</choice>" +
                "<choice value=\"v3\">I can do this well with minimal support.</choice>" +
                "<choice value=\"v4\">I can do this well on my own.</choice>" +
                "</input>" +
                "<part>" +
                "<response match=\"v1\" score=\"1\" />" +
                "<response match=\"v2\" score=\"1\" />" +
                "<response match=\"v3\" score=\"1\" />" +
                "<response match=\"v4\" score=\"1\" />" +
                "</part>" +
                "</fill_in_the_blank>"));

        assertTrue(str.contains("<short_answer id=\"short_multi\" grading=\"instructor\">" +
                "<body>" +
                "<table>" +
                "<tr>" +
                "<th>Expression to be evaluated</th>" +
                "<th>Your guess for <em style=\"italic\">Value</em> obtained and <em style=\"italic\">Type</em> of value obtained</th>" +
                "</tr>" +
                "<tr>" +
                "<td>50.0 + 25</td><td><input_ref input=\"ex1_1_1_width100\" /></td>" +
                "</tr>" +
                "<tr>" +
                "<td>100 / 50</td><td><input_ref input=\"ex1_1_2_width100\" /></td>" +
                "</tr>" +
                "<tr>" +
                "<td>100.0 / 50.0</td><td><input_ref input=\"ex1_1_3_width100\" /></td>" +
                "</tr>" +
                "<tr>" +
                "<td>100.0 / 50</td><td><input_ref input=\"ex1_1_4_width100\" /></td>" +
                "</tr>" +
                "<tr>" +
                "<td>100 // 50</td><td><input_ref input=\"ex1_1_5_width100\" /></td>" +
                "</tr>" +
                "</table>" +
                "</body>"));

        return str;
      }

    };

    pipeline.execute();
  }

  @Test
  public void shouldHandleOrganization() throws Exception {

    final ResourceTestPipeline pipeline = new ResourceTestPipeline(Xml2JsonTest.class.getResource("test-resources/organization.xml"),
        "x-oli-organization") {

        public String afterDocToString(final String str) {

        // check newlines removed in paragraph elements:
        assertTrue(str.contains(
                "<!DOCTYPE organization PUBLIC \"-//Carnegie Mellon University//DTD Content Organization Simple 2" +
                ".3//EN\" \"http://oli.web.cmu.edu/dtd/oli_content_organization_simple_2_3.dtd\">" +
                "<organization id=\"exp-theme-proof-2.0_default\" version=\"1.0\">" +
                "<title>Theme Proof</title>" +
                "<description>Empty project template for authoring OLI content packages.</description>" +
                "<audience>This organization is intended as an example for OLI content authors.</audience>" +
                "<sequences>" +
                "<sequence id=\"Theme-Start\" category=\"content\" audience=\"all\">" +
                "<title>Theme Proof</title>" +
                "<module id=\"assessment\">" +
                "<title>Assessment Tests</title>" +
                "<section id=\"test_cases\">" +
                "<title>Beta 1</title>" +
                "<item scoring_mode=\"default\">" +
                "<resourceref idref=\"hints_testing\" />" +
                "</item>" +
                "</section>" +
                "</module>" +
                "<module id=\"intro\">" +
                "<title>Some Examples</title>" +
                "<section id=\"created\">" +
                "<title>Created Examples</title>" +
                "<item scoring_mode=\"default\">" +
                "<resourceref idref=\"text_content_1\" />" +
                "</item>" +
                "<item scoring_mode=\"default\">" +
                "<resourceref idref=\"text_content_1\" />" +
                "</item>" +
                "<item scoring_mode=\"default\">" +
                "<resourceref idref=\"core_purpose_types\" />" +
                "</item>" +
                "<item scoring_mode=\"default\">" +
                "<resourceref idref=\"other_purpose_types\" />" +
                "</item>" +
                "<item scoring_mode=\"default\">" +
                "<resourceref idref=\"complex_composite\" />" +
                "</item>" +
                "<item scoring_mode=\"default\">" +
                "<resourceref idref=\"youtube\" />" +
                "</item>" +
                "<item scoring_mode=\"default\">" +
                "<resourceref idref=\"threeWidget\" />" +
                "</item>" +
                "<item scoring_mode=\"default\">" +
                "<resourceref idref=\"allformulas-sort-uniq\" />" +
                "</item>" +
                "</section>" +
                "<section id=\"actual\">" +
                "<title>Actual (Representative) Course Pages</title>" +
                "<item scoring_mode=\"default\">" +
                "<resourceref idref=\"_u2_m06_couples02\" />" +
                "</item>" +
                "<item scoring_mode=\"default\">" +
                "<resourceref idref=\"_u2_m07_loads01\" />" +
                "</item>" +
                "<item scoring_mode=\"default\">" +
                "<resourceref idref=\"dig7\" />" +
                "</item>" +
                "<item scoring_mode=\"default\">" +
                "<resourceref idref=\"_u4_m04_behavior_of_proportion2\" />" +
                "</item>" +
                "<item scoring_mode=\"default\">" +
                "<resourceref idref=\"lecture_5\" />" +
                "</item>" +
                "<item scoring_mode=\"default\">" +
                "<resourceref idref=\"_u5_m2_01\" />" +
                "</item>" +
                "<item purpose=\"learnbydoing\" scoring_mode=\"default\">" +
                "<resourceref idref=\"_u5_m2_ex1\" />" +
                "</item>" +
                "</section>" +
                "</module>" +
                "</sequence>" +
                "</sequences>" +
                "</organization>"));

        return str;
      }

    };

    pipeline.execute();
  }

}