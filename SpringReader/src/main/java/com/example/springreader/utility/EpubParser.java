package com.example.springreader.utility;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Slf4j //Creates a static log for me to use
public class EpubParser {


    public static Map<String, Object> parseMeta(File epubFile){
        Map<String, Object> response = new HashMap<>();

        try(ZipFile zipFile = new ZipFile(epubFile)){
            ZipEntry containerEntry = zipFile.getEntry("META-INF/container.xml");
            Document containerDocument = parseXML(zipFile.getInputStream(containerEntry));

            NodeList containerRootFiles = containerDocument.getElementsByTagName("rootfile");

            String opfFilePath = containerRootFiles.item(0).getAttributes().getNamedItem("full-path").getTextContent();

            ZipEntry opfEntry = zipFile.getEntry(opfFilePath);
            Document opfDocument = parseXML(zipFile.getInputStream(opfEntry));

            String title = opfDocument.getElementsByTagName("dc:title").item(0).getTextContent();
            String author = opfDocument.getElementsByTagName("dc:creator").item(0).getTextContent();
            response.put("title", title);
            response.put("author", author);

            //TABLE OF CONTENTS - There should be a toc.ncx file that looks like this:
            /**
             * <?xml version='1.0' encoding='UTF-8'?>
             * <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1" xml:lang="en">
             *   <head>
             *     <meta name="dtb:uid" content="http://www.gutenberg.org/11"/>
             *     <meta name="dtb:depth" content="1"/>
             *     <meta name="dtb:generator" content="Ebookmaker 0.13.4 by Project Gutenberg"/>
             *     <meta name="dtb:totalPageCount" content="0"/>
             *     <meta name="dtb:maxPageNumber" content="0"/>
             *   </head>
             *   <docTitle>
             *     <text>Alice's Adventures in Wonderland</text>
             *   </docTitle>
             *   <navMap>
             *     <navPoint id="np-1" playOrder="1">
             *       <navLabel>
             *         <text>Alice’s Adventures in Wonderland</text>
             *       </navLabel>
             *       <content src="229714655232534212_11-h-0.htm.html#pgepubid00000"/>
             *     </navPoint>
             *     <navPoint id="np-2" playOrder="2">
             *       <navLabel>
             *         <text>THE MILLENNIUM FULCRUM EDITION 3.0</text>
             *       </navLabel>
             *       <content src="229714655232534212_11-h-0.htm.html#pgepubid00001"/>
             *     </navPoint>
             *     <navPoint id="np-3" playOrder="3">
             *       <navLabel>
             *         <text>Contents</text>
             *       </navLabel>
             *       <content src="229714655232534212_11-h-0.htm.html#pgepubid00002"/>
             *     </navPoint>
             *     <navPoint id="np-4" playOrder="4">
             *       <navLabel>
             *         <text>CHAPTER I. Down the Rabbit-Hole</text>
             *       </navLabel>
             *       <content src="229714655232534212_11-h-1.htm.html#pgepubid00003"/>
             *     </navPoint>
             */


            NodeList manifestItems = opfDocument.getElementsByTagName("item");
            String tocHref = "";
            for( int i = 0; i < manifestItems.getLength(); i++){
                String mediaType = manifestItems.item(i).getAttributes().getNamedItem("media-type").getTextContent();
                //only our toc.ncx has this media type
                if(mediaType.equals("application/x-dtbncx+xml")){
                    tocHref = manifestItems.item(i).getAttributes().getNamedItem("href").getTextContent();
                    break;
                }
            }

            log.info(tocHref);


            if(tocHref.isEmpty()){
                log.info("Could not find toc.ncx");
                //Do something
            }
            else{
                //check if in root of zip or not
                String tocPath = "";
                if(Paths.get(opfFilePath).getParent() != null){
                    tocPath = Paths.get(opfFilePath).getParent().resolve(tocHref).toString();
                }
                else{
                    tocPath = tocHref;
                }
                log.info("tocPath is: " + tocPath);

                tocPath = tocPath.replace("\\", "/");
                ZipEntry tocEntry = zipFile.getEntry(tocPath);
                if(tocEntry == null){
                    log.error("TOC not found at: {}", tocPath);
                }
                else{
                    Document tocDoc = parseXML(zipFile.getInputStream(tocEntry));
                    NodeList navPoints = tocDoc.getElementsByTagName("navPoint");

                    //A list of maps, each map will have a title, contentPath, and the index of the chapter
                    List<Map<String,String>> tocList = new ArrayList<>();

                    for(int i =0; i < navPoints.getLength(); i++){
                        Element navPoint = (Element) navPoints.item(i);
                        String chapterTitle = "";

                        NodeList navLabels = tocDoc.getElementsByTagName("navLabel");

                        chapterTitle = navLabels.item(i).getTextContent();
                        log.info("chapterTitle is : {}", chapterTitle);

                        NodeList contentList = navPoint.getElementsByTagName("content");
                        String contentSrc = "";

                        //log.info("ContestList length is: {}", contentList.getLength());


                        if(contentList.getLength() > 0){
                            contentSrc = contentList.item(0).getAttributes().getNamedItem("src").getTextContent();
                            log.info("ContentSrc is : {}", contentSrc);
                        }

                        String index = String.valueOf(i);

                        log.info("Index is : {}", index);
                        Map<String, String> tocMap = new HashMap<>();


                        //Title index and path of the chapter
                        tocMap.put("Title", chapterTitle);
                        tocMap.put("contentSrc", contentSrc);
                        tocMap.put("index", index);
                        tocList.add(tocMap);

                    }
                    response.put("toc", tocList);
                }

            }

        }
        catch (Exception e){
            log.error("Error opening epub", e);
        }



        return response;
    }

    public static Map<String, Object> parseContent(File epubFile, int chapterIndex) {
        Map<String, Object> response = new HashMap<>();
        String chapterContent = "";


        try(ZipFile zipFile = new ZipFile(epubFile)) {
            System.out.println("Epub file opened");

            //Find the container.xml file, should be inside META-INF directory
            //ZipEntry represents a single file in a zip archive
            //Doesn't hold the actual file data, just metadata
            //Need to use getInputStream to get the actual file data
            ZipEntry containerEntry = zipFile.getEntry("META-INF/container.xml");


            //Parse the container.xml to find our OPF file
            //Need to get the rootfile element from the container.xml
            //Which is inside the rootfiles element
            //Which has an attribute full-path that contains the path to the OPF file

            Document containerDocument = parseXML(zipFile.getInputStream(containerEntry));

            //NodeList is a collection of XML elements, in our case it's just one, 'rootfile'.
            NodeList containerRootFiles = containerDocument.getElementsByTagName("rootfile");


            //System.out.println(containerRootFiles.getLength());

            //.item(0) returns the first element in the NodeList, in our case there is only one, our rootfile element
            //getAttributes() returns a NamedNodeMap, which is a collection of attributes
            //getNamedItem("full-path") returns the attribute with the name "full-path"
            //getNodeValue() returns the value of the attribute
            //System.out.println(containerRootFiles.item(0).getAttributes().getNamedItem("full-path").getNodeValue());


            String opfFilePath = containerRootFiles.item(0).getAttributes().getNamedItem("full-path").getTextContent();
            System.out.println(opfFilePath);
            log.info("OPF file path: {}", opfFilePath);


            //Get our opfDocument, holds all the metadata for the book
            ZipEntry opfEntry = zipFile.getEntry(opfFilePath);
            Document opfDocument = parseXML(zipFile.getInputStream(opfEntry));

            //Get what we want from the opfFile

            //Title
//            NodeList titleNodeList = opfDocument.getElementsByTagName("dc:title");
//            String title = titleNodeList.item(0).getTextContent();
//
//            //Author
//            NodeList authorNodeList = opfDocument.getElementsByTagName("dc:creator");
//            String author = authorNodeList.item(0).getTextContent();

            //log.info(titleNodeList.item(0).getTextContent());
            //Spine processing, example:
            /**
             * <spine toc="ncx">
             *     <itemref idref="coverpage-wrapper" linear="yes"/>
             *     <itemref idref="pg-header" linear="yes"/>
             *     <itemref idref="item3" linear="yes"/>
             *     <itemref idref="item4" linear="yes"/>
             *     <itemref idref="item5" linear="yes"/>
             *     <itemref idref="item6" linear="yes"/>
             *     <itemref idref="pg-footer" linear="yes"/>
             *   </spine>
             */

            //spineList is our how we get our reading order
            NodeList spineList = opfDocument.getElementsByTagName("itemref");
            String chapterSpine = spineList.item(chapterIndex).getAttributes().getNamedItem("idref").getTextContent();
            //log.info("corresponding id of my first chapter in the manifest: " + chapterSpine);



            //Find our chapter in the manifest.
            /**
             *  <manifest>
             *     <!--Image: 800 x 1104 size=53578 q=20-->
             *     <item href="4308839259886326920_cover.jpg" id="id-1680337984273111657" media-type="image/jpeg"/>
             *     <item href="pgepub.css" id="item1" media-type="text/css"/>
             *     <item href="0.css" id="item2" media-type="text/css"/>
             *     <!--Chunk: size=4000 Split on div.chapter-->
             *     <item href="229714655232534212_11-h-0.htm.html" id="pg-header" media-type="application/xhtml+xml"/>
             *     <!--Chunk: size=12464 Split on div.chapter-->
             *     <item href="229714655232534212_11-h-1.htm.html" id="item3" media-type="application/xhtml+xml"/>
             *     <!--Chunk: size=11903 Split on div.chapter-->
             *     <item href="229714655232534212_11-h-2.htm.html" id="item4" media-type="application/xhtml+xml"/>
             */


            NodeList manifestList = opfDocument.getElementsByTagName("item");
            //String chapterHref = manifestList.item(4).getAttributes().getNamedItem("href").getTextContent();

            String chapterHref = "";
            for(int i =0 ; i <manifestList.getLength(); i++){
                if(manifestList.item(i).getAttributes().getNamedItem("id").getTextContent().equals(chapterSpine)){
                    chapterHref = manifestList.item(i).getAttributes().getNamedItem("href").getTextContent();
                    break;
                }
            }
            if(chapterHref == null){
                log.error("Chapter not found");
                return null;
            }

            //log.info("href of the first chapter: " + chapterHref);
            log.info("Chapter href: {}", chapterHref);


            //Have to fix the path as our chapterHref is giving a relative path to the OEBPS directory
            //ZipEntry needs path from the root of the Zip
            String chapterPath = "";
            if(Paths.get(opfFilePath).getParent() != null){
                chapterPath = Paths.get(opfFilePath).getParent().resolve(chapterHref).toString();
            }
            else{
                chapterPath = chapterHref;
            }



            //log.info("Our final chapter 1 path " + chapter1Path);

            //Need to replace all of our backslashes with forward ones in order for our ZipEntry class to work.
            chapterPath = chapterPath.replace("\\", "/");

            ZipEntry chapterZipEntry = zipFile.getEntry(chapterPath);
            //ZipEntry testPath = zipFile.getEntry("OEBPS/229714655232534212_11-h-1.htm.html");


            log.info(chapterZipEntry.toString());

            //Read the chapter file
            String chapter1ContentHTML = new String(zipFile.getInputStream(chapterZipEntry).readAllBytes(), StandardCharsets.UTF_8);
            //log.info(chapter1Content);
            chapterContent = Jsoup.parse(chapter1ContentHTML).text();
            response.put("chapterContent", chapterContent);

        } catch (Exception e) {
            System.out.println("Error opening the epub," + e);
        }


        return response;

    }

    private static Document parseXML(InputStream input) {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try {
            return factory.newDocumentBuilder().parse(input);
        } catch (Exception e) {
            System.out.println("Error parsing the XML file," + e);
        }
        return null;
    }
}
