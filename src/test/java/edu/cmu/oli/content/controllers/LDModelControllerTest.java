package edu.cmu.oli.content.controllers;

import edu.cmu.oli.content.ContentServiceException;
import edu.cmu.oli.content.boundary.managers.ContentResourceManager;
import edu.cmu.oli.content.models.persistance.entities.ContentPackage;
import edu.cmu.oli.content.models.persistance.entities.Resource;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Raphael Gachuhi
 */
public class LDModelControllerTest {

    private LDModelControllerImpl cut;
    private String skillsData = "Skill\tTitle\tp\tgamma0\tgamma1\tlambda0\n"
            + "_u1_identify_vital_SKILL\tIdentify functions vital to human life\t0.70\t0.70\t0.70\t1.00\n"
            + "_u1_Explain_vital_SKILL\tExplain how organ systems contribute vital functions\t0.70\t0.70\t0.70\t1.00\n"
            + "_u1_identify_vital_exchange_SKILL\tvital function - exchange with environment\t0.70\t0.70\t0.70\t1.00\n"
            + "_u1_identify_vital_transport_SKILL\tvital function - transport of fluids\t0.70\t0.70\t0.70\t1.00\n"
            + "_u1_identify_vital_structure_SKILL\tvital function - structure and support\t0.70\t0.70\t0.70\t1.00\n"
            + "_u1_identify_vital_control_SKILL\tvital function - control and regulation\t0.70\t0.70\t0.70\t1.00";

    private String losData = "ccdm\t1.2\t\t\t\t\t\t\t\t\t\t\t\n"
            + "Learning objective\tLow Opportunity\tMin. Practice\tLow Cutoff\tModerate Cutoff\tSkill1\tSkill2\tSkill3\tSkill4\tSkill5\tSkill6\tSkill7\tSkill8\n"
            + "rep_direct_causes_effects\tN\t2\t1.5\t2.5\tdiff_dir_vs_undir_edge\tdist_when_2_var_r_adj_or_not\tidentify_dir_causes_p_dir_effects_c_var\t\t\t\t\t";
    private ContentPackage contentPackage;

    @Before
    public void setUp() throws Exception {
        this.contentPackage = mock(ContentPackage.class);
        this.cut = new LDModelControllerImpl();
        this.cut.contentResourceManager = mock(ContentResourceManager.class);
        Logger mockLogger = mock(Logger.class);
        this.cut.log = mockLogger;

        when(this.cut.contentResourceManager.doCreate(anyString(), anyString(), any(), anyString(), anyString()))
                .thenReturn(new Resource());
        when(this.cut.contentResourceManager.doUpdate(anyString(), any(), any(), anyString(), anyString(), anyString()))
                .thenReturn(new Resource());

        when(this.contentPackage.getId()).thenReturn("ccdm");
        when(this.contentPackage.getVersion()).thenReturn("1.2");
        // getSkillsIndex
        // getObjectivesIndex

    }

    @Test
    public void importSkills() throws ContentServiceException {
        // Optional<JsonElement> skills = this.cut.importSkills(contentPackage,
        // "xxxx-skills.tsv", skillsData);
        // System.out.println(AppUtils.gsonBuilder().create().toJson(skills.isPresent()?skills.get():
        // JsonNull.INSTANCE));
    }

    @Test
    public void importLOsSkillsMap() throws ContentServiceException {
        // List<String> strings = this.cut.importLOsSkillsMap(contentPackage,
        // "xxx-los.tsv", losData);
    }
}