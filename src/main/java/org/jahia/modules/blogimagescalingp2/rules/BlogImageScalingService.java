package org.jahia.modules.blogimagescalingp2.rules;

import javax.jcr.Node;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.nodetype.NodeType;
import java.io.*;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Iterator;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.drools.ObjectFilter;
import org.drools.spi.KnowledgeHelper;
import org.im4java.core.ConvertCmd;
import org.im4java.core.IMOperation;
import org.jahia.api.Constants;
import org.jahia.services.content.JCRContentUtils;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.rules.AddedNodeFact;
import org.jahia.services.content.rules.ChangedPropertyFact;
import org.jahia.services.image.Image;
import org.jahia.services.image.ImageMagickImage;
import org.jahia.services.image.ImageMagickImageService;
import org.jahia.settings.SettingsBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by rvt on 2/23/14.
 */
public class BlogImageScalingService {
    private static final Logger logger = LoggerFactory.getLogger(BlogImageScalingService.class);
    private static final String BLOGMIX_BLOGIMAGESCALINGFOLDER = "blogmix:blogimagescalingfolder";

    private static BlogImageScalingService instance;
    private static File contentTempFolder;
    private static ImageMagickImageService imageMagickImageService;

    public static BlogImageScalingService getInstance() {
        if (instance == null) {
            synchronized (BlogImageScalingService.class) {
                if (instance == null) {
                    instance = new BlogImageScalingService();
                    contentTempFolder = new File(SettingsBean.getInstance().getTmpContentDiskPath());
                    if (!contentTempFolder.exists()) {
                        contentTempFolder.mkdirs();
                    }
                    imageMagickImageService = ImageMagickImageService.getInstance();
                }
            }
        }
        return instance;
    }

    /**
     * Cropscale image rule
     *
     * @param changedPropertyfact
     * @param name
     * @param drools
     * @throws Exception
     */
    public void cropScale(ChangedPropertyFact changedPropertyfact, String name, KnowledgeHelper drools) throws Exception {
        JCRNodeWrapper definitionNode = changedPropertyfact.getNode().getNode();
        final JCRPropertyWrapper property = definitionNode.getProperty(changedPropertyfact.getName());
        final JCRSessionWrapper session = property.getSession();
        JCRNodeWrapper node = session.getNodeByIdentifier(property.getString());

        try {
            // Get the requested width and height of a image
            int requestedWidth = (int) definitionNode.getProperty("width").getLong();
            int requestedHeight = (int) definitionNode.getProperty("height").getLong();

            // Create a new cropScaled image
            cropScaleNode(new AddedNodeFact(node), name, requestedWidth, requestedHeight, drools);
        } catch (PathNotFoundException e) {
            logger.error("Property to process image not found" + e.getMessage());
        } catch (Exception e) {
            logger.error("General exception processing image" + e.getMessage());
        }
    }

    /**
     * Cropscale image rule
     *
     * @param propertyWrapper
     * @param name
     * @param drools
     * @throws Exception
     */
    public void cropScale(ChangedPropertyFact propertyWrapper, String name, int requestedWidth, int requestedHeight, KnowledgeHelper drools) throws Exception {
        JCRNodeWrapper settingsNode = propertyWrapper.getNode().getNode();
        final JCRPropertyWrapper property = settingsNode.getProperty(propertyWrapper.getName());
        final JCRSessionWrapper session = property.getSession();
        JCRNodeWrapper node = session.getNodeByIdentifier(property.getString());

        try {
            // Get the requested width and height of a image
            cropScaleNode(new AddedNodeFact(node), name, requestedWidth, requestedHeight, drools);
        } catch (Exception e) {
            logger.error("General exception processing image" + e.getMessage());
        }
    }


    /**
     * Crop scale a image in a folder when a mixin is set on this node or recursive going down the rootline
     *
     * @param imageNode
     * @param name
     * @param recursive
     * @param drools
     * @throws Exception
     */
    public void cropScaleFolderImage(AddedNodeFact imageNode, String name, boolean recursive, KnowledgeHelper drools) throws Exception {
        JCRNodeWrapper folder=imageNode.getNode().getParent();
        while (folder!=null) {
            if (hasMixin(folder.getMixinNodeTypes(), BLOGMIX_BLOGIMAGESCALINGFOLDER)) {
                if (folder.hasProperty("blogmix:width") && folder.hasProperty("blogmix:height")) {
                    int requestedWidth = (int) folder.getProperty("blogmix:width").getLong();
                    int requestedHeight = (int) folder.getProperty("blogmix:height").getLong();
                    cropScaleNode(imageNode, name, requestedWidth, requestedHeight, drools);
                } else {
                    logger.warn("Mixin "+BLOGMIX_BLOGIMAGESCALINGFOLDER+" found but width or height wasn't filled in.");
                }
                break;
            };
            if (recursive==false) {
                break;
            }
            folder = JCRContentUtils.getParentOfType(folder, Constants.NT_FOLDER);
        }
    }

    /**
     * Test if a node has a specific mixin added
     *
     * @param mixinNodeTypes
     * @param type
     * @return
     */
    private boolean hasMixin(NodeType[] mixinNodeTypes, String type)  {
        for (NodeType mixin : mixinNodeTypes) {
            if (mixin.isNodeType(type)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Crop scale an image
     *
     * @param imageNode
     * @param nodeName
     * @param requestedWidth
     * @param requestedHeight
     * @param drools
     * @throws Exception
     */
    private void cropScaleNode(AddedNodeFact imageNode, String nodeName, int requestedWidth, int requestedHeight, KnowledgeHelper drools) throws Exception {
        long timer = System.currentTimeMillis();

        AddedNodeFact thumbNode = null;
        File croppedImage = null;

        // test if the node already existed and to a update or create a new node
        if (imageNode.getNode().hasNode(nodeName)) {
            JCRNodeWrapper node = imageNode.getNode().getNode(nodeName);
            Calendar thumbDate = node.getProperty("jcr:lastModified").getDate();
            Calendar contentDate = imageNode.getNode().getNode("jcr:content").getProperty("jcr:lastModified").getDate();
            if (contentDate.after(thumbDate)) {
                croppedImage = cropScaleNode(imageNode, requestedWidth, requestedHeight, drools);
                if (croppedImage == null) {
                    return;
                }
                thumbNode = new AddedNodeFact(node);
            }
        } else {
            croppedImage = cropScaleNode(imageNode, requestedWidth, requestedHeight, drools);
            if (croppedImage == null) {
                return;
            }

            thumbNode = new AddedNodeFact(imageNode, nodeName, "jnt:resource", drools);
            if (thumbNode.getNode() != null) {
                drools.insert(thumbNode);
            }
        }

        if (thumbNode != null && thumbNode.getNode() != null) {
            drools.insert(new ChangedPropertyFact(thumbNode, Constants.JCR_DATA, croppedImage, drools));
            drools.insert(new ChangedPropertyFact(thumbNode, Constants.JCR_MIMETYPE, imageNode.getMimeType(), drools));
            drools.insert(new ChangedPropertyFact(thumbNode, Constants.JCR_LASTMODIFIED, new GregorianCalendar(), drools));
        }

        if (logger.isDebugEnabled()) {
            logger.debug("crop for node {} created in {} ms", new Object[]{imageNode.getNode().getPath(), System.currentTimeMillis() - timer});
        }
    }

    /**
     * Crop a given image within AddedNodeFact and create a new image with suffix
     *
     * @param imageNode
     * @param requestedWidth
     * @param requestedHeight
     * @param drools
     * @return
     * @throws Exception
     */
    private File cropScaleNode(AddedNodeFact imageNode, int requestedWidth, int requestedHeight, KnowledgeHelper drools) throws Exception {
        String fileExtension = FilenameUtils.getExtension(imageNode.getName());

        // Don't crap when the image was smaller then the original
        if (isSmallerThan(imageNode.getNode(), requestedWidth, requestedHeight)) {
            logger.info("Selected image was smaller then required image of {}x{} not cropping this image", requestedWidth, requestedHeight);

            // no need to resize the small image for the cropped
            final File f = File.createTempFile("thumb", StringUtils.isNotEmpty(fileExtension) ? "." + fileExtension : null, contentTempFolder);
            JCRContentUtils.downloadFileContent(imageNode.getNode(), f);
            f.deleteOnExit();
            return f;
        }

        Image iw = getImageWrapper(imageNode, drools);
        if (iw == null) {
            return null;
        }

        // Generate a tmp file for the image to be placed in bu resizeImage
        final File f = File.createTempFile("blogimagescaling", StringUtils.isNotEmpty(fileExtension) ? "." + fileExtension : null, contentTempFolder);
        f.deleteOnExit();

        // Rescale the image
        if (cropScaleImage(iw, f, requestedWidth, requestedHeight)) {
            return f;
        } else {
            return null;
        }
    }

    /**
     * Test if node is smaller or equal then width/height
     *
     * @param node
     * @param width
     * @param height
     * @return
     */
    protected boolean isSmallerThan(JCRNodeWrapper node, int width, int height) {
        long iWidth = 0;
        long iHeight = 0;

        try {
            iWidth = node.getProperty("j:width").getLong();
            iHeight = node.getProperty("j:height").getLong();
        } catch (RepositoryException e) {
            if (logger.isDebugEnabled()) {
                logger.warn("Error reading j:width/j:height properties on node " + node.getPath(), e);
            } else {
                logger.warn("Error reading j:width/j:height properties on node " + node.getPath() + ". Casue: " + e.getMessage());
            }
        }

        return iWidth > 0 && iHeight > 0 && iWidth <= width && iHeight <= height;
    }


    /**
     * Get the image from the node, copied from ImageService rule
     *
     * @param imageNode
     * @param drools
     * @return
     * @throws Exception
     */
    private Image getImageWrapper(final AddedNodeFact imageNode, KnowledgeHelper drools) throws Exception {
        Iterator<?> it = drools.getWorkingMemory().iterateObjects(new ObjectFilter() {
            public boolean accept(Object o) {
                if (o instanceof Image) {
                    try {
                        return (((Image) o).getPath().equals(imageNode.getPath()));
                    } catch (RepositoryException e) {
                        e.printStackTrace();
                    }
                }
                return false;
            }
        });
        if (it.hasNext()) {
            return (Image) it.next();
        }

        Image iw = getImage(imageNode.getNode());
        if (iw == null) {
            return null;
        }
        drools.insertLogical(iw);
        return iw;
    }

    /**
     * Download the image from the JCR into a tmp file
     *
     * @param node
     * @return
     * @throws IOException
     * @throws RepositoryException
     */
    private Image getImage(JCRNodeWrapper node) throws IOException, RepositoryException {
        Node contentNode = node.getNode(Constants.JCR_CONTENT);
        String fileExtension = FilenameUtils.getExtension(node.getName());
        File tmp = File.createTempFile("image", StringUtils.isNotEmpty(fileExtension) ? "."
                + fileExtension : null);
        InputStream is = contentNode.getProperty(Constants.JCR_DATA).getBinary().getStream();
        OutputStream os = new BufferedOutputStream(new FileOutputStream(tmp));
        try {
            IOUtils.copy(is, os);
        } finally {
            IOUtils.closeQuietly(os);
            IOUtils.closeQuietly(is);
        }
        return new ImageMagickImage(tmp, tmp.getPath());
    }

    /**
     * Crop scale an image
     *
     * @param i
     * @param outputFile
     * @param maxWidth
     * @param maxHeight
     * @return
     * @throws IOException
     */
    public boolean cropScaleImage(Image i, File outputFile, int maxWidth, int maxHeight) throws IOException {
        try {
            // create command
            ConvertCmd cmd = new ConvertCmd();

            // create the operation, add images and operators/options
            IMOperation op = new IMOperation();
            op.addImage(i.getPath());
            op.background("none");
            op.resize(maxWidth, maxHeight, "^");
            op.gravity("center");
            op.crop(maxWidth, maxHeight, 0, 0);
            op.p_repage();
            op.addImage(outputFile.getPath());

            // logger.info("Running ImageMagic command: convert " + op);
            cmd.run(op);
        } catch (Exception e) {
            logger.error("Error cropping image " + i.getPath() + " to size " + maxWidth + "x" + maxHeight + ": " + e.getLocalizedMessage());
            if (logger.isDebugEnabled()) {
                logger.debug("Error cropping image " + i.getPath() + " to size " + maxWidth + "x" + maxHeight, e);
            }
            return false;
        }
        return true;
    }


}
