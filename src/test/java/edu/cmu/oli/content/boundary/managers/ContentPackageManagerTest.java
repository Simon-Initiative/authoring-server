package edu.cmu.oli.content.boundary.managers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import edu.cmu.oli.content.models.persistance.entities.ContentPackage;
import edu.cmu.oli.content.security.AppSecurityContext;
import edu.cmu.oli.content.security.AppSecurityController;
import org.junit.Before;
import org.junit.Test;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.util.Arrays;
import java.util.HashSet;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Raphael Gachuhi
 */
public class ContentPackageManagerTest {

    private ContentPackageManager cut;

    @Before
    public void setUp() throws Exception {
        this.cut = new ContentPackageManager();
        this.cut.em = mock(EntityManager.class);
        this.cut.securityManager = mock(AppSecurityController.class);
        TypedQuery mockQuery = mock(TypedQuery.class);

        when(this.cut.em.createNamedQuery(anyString(), any())).thenReturn(mockQuery);
        when(mockQuery.getResultList()).thenReturn(Arrays.asList(new ContentPackage("pkgOne", "1.0"), new ContentPackage("pkgTwo", "1.2")));
        when(this.cut.securityManager.authorize(any(), anyList(), anyString(), anyString(), anyList())).thenReturn(new HashSet<>());
    }

    @Test
    public void return_all_packages() {
        JsonElement all = this.cut.all(mock(AppSecurityContext.class));
        assertTrue(all.isJsonArray());
        assertThat(((JsonArray) all).size(), equalTo(2));
        JsonArray array = (JsonArray) all;
        assertThat(((JsonObject) array.get(0)).get("id").getAsString(), equalTo("pkgOne"));
    }

    @Test
    public void editablePackages() throws Exception {
    }

    @Test
    public void loadPackage() throws Exception {
    }

    @Test
    public void updatePackage() throws Exception {
    }

    @Test
    public void createPackage() throws Exception {
    }

    @Test
    public void deletePackage() throws Exception {
    }

}