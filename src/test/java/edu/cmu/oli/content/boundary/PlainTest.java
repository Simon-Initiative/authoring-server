package edu.cmu.oli.content.boundary;

import edu.cmu.oli.content.analytics.DatasetBuilder;
import org.jdom2.JDOMException;
import org.junit.Test;

import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import static org.junit.Assert.assertTrue;

/**
 * @author Raphael Gachuhi
 */
public class PlainTest {

    @Test
    public void testNothing() throws JDOMException, IOException {
        // StringReader st = new StringReader(content);
        // SAXBuilder builder = new SAXBuilder(XMLReaders.NONVALIDATING);
        // Document document = builder.build(st);
        // XPathFactory xFactory = XPathFactory.instance();
        // XPathExpression<Element> xexpression = xFactory.compile("//*[contains(@*,
        // 'webcontent')]", Filters.element());
        // List<Element> elements = xexpression.evaluate(document.getRootElement());
        // elements.forEach(e->{
        // System.out.println("element " + e.getName());
        // List<Attribute> ats = e.getAttributes();
        // ats.forEach(a->{
        // if(a.getValue().contains("webcontent")) {
        // System.out.println("attribute " + a.getName());
        // }
        // });
        // });
        // Path path = Paths.get("/oli/content/somefile.xml");
        // Path relativize = path.resolve("../otherfile.xml");
        // System.out.println(relativize.normalize());
        // System.out.println(Paths.get("/oli").relativize(relativize.normalize()));
        // assertTrue(!elements.isEmpty());

        // StringReader st = new StringReader(content);
        // SAXBuilder builder = new SAXBuilder(XMLReaders.NONVALIDATING);
        // Document document = builder.build(st);
        // XPathFactory xFactory = XPathFactory.instance();
        //// //cmd:concept |
        // XPathExpression<Element> xexpression = xFactory.compile("//*[contains(@src,
        // 'webcontent/')] | //*[contains(text(),'webcontent/')]", Filters.element(),
        // null,
        // Namespace.getNamespace("cmd",
        // "http://oli.web.cmu.edu/content/metadata/2.1/"));
        // List<Element> images = xexpression.evaluate(document);
        // images.forEach(e->{
        // JsonObject ob = new JsonObject();
        // getParent(e, ob);
        // System.out.println(AppUtils.gsonBuilder().create().toJson(ob));
        // //System.out.println(XPathHelper.getAbsolutePath(e));
        // });

        // DirectoryUtils du = new DirectoryUtils();
        //
        // ZipInputStream zis = new
        // ZipInputStream(Files.newInputStream(Paths.get(File.separator+"oli"+File.separator+"content"+
        // File.separator+"ldmodel.zip")));
        //
        // ZipEntry entry = zis.getNextEntry();
        //
        // assertNotNull(entry);
        //
        // while (entry != null) {
        // String fileName = entry.getName();
        // fileName = fileName.replaceAll(" ", "_");
        // String substring = fileName.substring(0, fileName.lastIndexOf('.'));
        // substring = substring.replaceAll("\\.", "");
        // fileName = substring + fileName.substring(fileName.lastIndexOf('.'));
        //
        // // Force upload into webcontent directory
        // if (!fileName.contains("webcontent")) {
        // fileName = "webcontent_test" + File.separator + "ld_model" + File.separator +
        // fileName;
        // }
        // String uploadLocation = File.separator+"oli"+File.separator+"content"+
        // File.separator + fileName;
        // Path uploadPath = FileSystems.getDefault().getPath(uploadLocation);
        //
        // assertTrue(!uploadPath.toFile().exists());
        //
        // du.createDirectories(uploadLocation);
        //
        // du.saveFile(zis, uploadPath);
        //
        // entry = zis.getNextEntry();
        // }

        List<Date> listDates = new ArrayList<Date>();
        DateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd");

        try {
            listDates.add(dateFormatter.parse("2013-09-30"));
            listDates.add(dateFormatter.parse("2013-07-06"));
            listDates.add(dateFormatter.parse("2013-11-28"));
        } catch (ParseException ex) {
            System.err.print(ex);
        }

        System.out.println("Before sorting: " + listDates);

        Collections.sort(listDates, new Comparator<Date>() {
            @Override
            public int compare(Date o1, Date o2) {
                return o2.compareTo(o1);
            }
        });

        System.out.println("After sorting: " + listDates);

        List<Integer> integerList = new ArrayList<>();
        integerList.add(new Integer(1));
        integerList.add(new Integer(2));
        integerList.add(new Integer(3));
        Collections.sort(integerList, new Comparator<Integer>() {
            @Override
            public int compare(Integer o1, Integer o2) {
                return o2.compareTo(o1);
            }
        });

        System.out.println("After sorting ints: " + integerList);
        assertTrue(true);
    }

}
