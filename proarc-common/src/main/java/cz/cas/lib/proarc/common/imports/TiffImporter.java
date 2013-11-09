/*
 * Copyright (C) 2011 Jan Pokorsky
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package cz.cas.lib.proarc.common.imports;

import cz.cas.lib.proarc.common.dao.BatchItem.ObjectState;
import cz.cas.lib.proarc.common.dublincore.DcStreamEditor;
import cz.cas.lib.proarc.common.fedora.BinaryEditor;
import cz.cas.lib.proarc.common.fedora.DigitalObjectException;
import cz.cas.lib.proarc.common.fedora.FedoraObject;
import cz.cas.lib.proarc.common.fedora.LocalStorage;
import cz.cas.lib.proarc.common.fedora.LocalStorage.LocalObject;
import cz.cas.lib.proarc.common.fedora.StringEditor;
import cz.cas.lib.proarc.common.fedora.XmlStreamEditor;
import cz.cas.lib.proarc.common.fedora.relation.RelationEditor;
import cz.cas.lib.proarc.common.imports.FileSet.FileEntry;
import cz.cas.lib.proarc.common.imports.ImportBatchManager.BatchItemObject;
import cz.cas.lib.proarc.common.imports.ImportProcess.ImportOptions;
import cz.cas.lib.proarc.common.mods.ModsStreamEditor;
import cz.cas.lib.proarc.common.mods.ModsUtils;
import cz.fi.muni.xkremser.editor.server.mods.ModsType;
import cz.incad.imgsupport.ImageMimeType;
import cz.incad.imgsupport.ImageSupport;
import java.awt.image.BufferedImage;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.imageio.stream.FileImageOutputStream;
import javax.ws.rs.core.MediaType;

/**
 * Requires Java Advanced Imaging support.
 * See http://www.oracle.com/technetwork/java/current-142188.html and
 * http://download.java.net/media/jai/builds/release/1_1_3/
 * jai-1_1_3-lib.zip is a platform independent version
 *
 * http://download.java.net/media/jai-imageio/builds/release/1.1/ fo jai_imageio-1.1.jar
 *
 * For maven, try to depend just on com.sun.media.jai_imageio.1.1 as kramerius common.
 * How to properly depend in pom see http://sahits.ch/blog/?p=1038
 *
 * @author Jan Pokorsky
 */
public final class TiffImporter {

    private static final Logger LOG = Logger.getLogger(TiffImporter.class.getName());
    private final ImportBatchManager ibm;

    public TiffImporter(ImportBatchManager ibm) {
        this.ibm = ibm;
    }

    public boolean accept(FileSet fileSet) {
        return isTiff(fileSet);
    }

    public BatchItemObject consume(FileSet fileSet, ImportOptions ctx) {
        FileEntry tiffEntry = findTiff(fileSet);
        // check tiff file
        if (tiffEntry == null) {
            return null;
        }

        File f = tiffEntry.getFile();
        String originalFilename = fileSet.getName();

        // creates FOXML and metadata
        LocalObject localObj = createObject(originalFilename, ctx);
        BatchItemObject batchLocalObject = ibm.addLocalObject(ctx.getBatch(), localObj);
        try {
            createMetadata(localObj, ctx);
            createRelsExt(localObj, f, ctx);
            createImages(ctx.getTargetFolder(), f, originalFilename, localObj);
            importOcr(fileSet, localObj, ctx);
            // XXX generate ATM
            // writes FOXML
            localObj.flush();
            ibm.addChildRelation(ctx.getBatch(), null, localObj.getPid());
            batchLocalObject.setState(ObjectState.LOADED);
        } catch (Throwable ex) {
            LOG.log(Level.SEVERE, f.toString(), ex);
            batchLocalObject.setState(ObjectState.LOADING_FAILED);
            batchLocalObject.setLog(ImportBatchManager.toString(ex));
        }
        ibm.update(batchLocalObject);

        return batchLocalObject;
    }

    private LocalObject createObject(String originalFilename, ImportOptions ctx) {
        File tempBatchFolder = ctx.getTargetFolder();
        LocalStorage storage = new LocalStorage();
        File foxml = new File(tempBatchFolder, originalFilename + ".foxml");
        LocalObject localObj = storage.create(foxml);
        localObj.setOwner(ctx.getUsername());
        return localObj;
    }

    private void createMetadata(LocalObject localObj, ImportOptions ctx) throws DigitalObjectException {
        String fedoraModel = ctx.getModel();
        // MODS
        ModsStreamEditor modsEditor = new ModsStreamEditor(localObj);
        String pageIndex = ctx.isGenerateIndices() ? String.valueOf(ctx.getConsumedFileCounter() + 1) : null;
        ModsType mods = modsEditor.createPage(localObj.getPid(), pageIndex, null, null);
        modsEditor.write(mods, 0, null);

        // DC
        DcStreamEditor dcEditor = new DcStreamEditor(localObj);
        dcEditor.write(mods, fedoraModel, 0, null);

        localObj.setLabel(ModsUtils.getLabel(mods, fedoraModel));
    }

    private void createRelsExt(LocalObject localObj, File f, ImportOptions ctx) throws DigitalObjectException {
        String fedoraModel = ctx.getModel();
        RelationEditor relEditor = new RelationEditor(localObj);
        relEditor.setModel(fedoraModel);
        relEditor.setDevice(ctx.getDevice());
        relEditor.setImportFile(f.getName());
        relEditor.write(0, null);
        // XXX use fedora-model:downloadFilename in RELS-INT or label of datastream to specify filename
    }

    private boolean isTiff(FileSet fileSet) {
        return findTiff(fileSet) != null;
    }

    private FileEntry findTiff(FileSet fileSet) {
        for (FileEntry entry : fileSet.getFiles()) {
            String mimetype = entry.getMimetype();
            if (ImageMimeType.TIFF.getMimeType().equals(mimetype)) {
                return entry;
            }
        }
        return null;
    }

    private void importOcr(FileSet fileSet, FedoraObject fo, ImportOptions options)
            throws IOException, DigitalObjectException {

        // XXX find filename.ocr.txt or generate OCR or nothing
        File tempBatchFolder = options.getTargetFolder();
        String originalFilename = fileSet.getName();
        FileEntry ocrEntry = findOcr(fileSet, options.getOcrFilePattern());
        if (ocrEntry != null) {
            File ocrFile = new File(tempBatchFolder, originalFilename + '.' + StringEditor.OCR_ID + ".txt");
            StringEditor.copy(ocrEntry.getFile(), options.getOcrCharset(), ocrFile, "UTF-8");
            XmlStreamEditor ocrEditor = fo.getEditor(StringEditor.ocrProfile());
            ocrEditor.write(ocrFile.toURI(), 0, null);
        }
    }

    private FileEntry findOcr(FileSet fileSet, String filePattern) {
        Pattern ocrPattern = Pattern.compile(filePattern);
        for (FileEntry entry : fileSet.getFiles()) {
            if (ocrPattern.matcher(entry.getFile().getName()).matches()) {
                return entry;
            }
        }
        return null;
    }

    private void createImages(File tempBatchFolder, File original, String originalFilename, LocalObject foxml)
            throws IOException, DigitalObjectException {
        
        BinaryEditor.dissemination(foxml, BinaryEditor.RAW_ID, BinaryEditor.IMAGE_TIFF)
                .write(original, 0, null);

        long start = System.nanoTime();
        BufferedImage tiff = ImageSupport.readImage(original.toURI().toURL(), ImageMimeType.TIFF);
        long endRead = System.nanoTime() - start;
        ImageMimeType imageType = ImageMimeType.JPEG;
        MediaType mediaType = MediaType.valueOf(imageType.getMimeType());

        start = System.nanoTime();
        String targetName = String.format("%s.full.%s", originalFilename, imageType.getDefaultFileExtension());
        File f = writeImage(tiff, tempBatchFolder, targetName, imageType);
        long endFull = System.nanoTime() - start;
        BinaryEditor.dissemination(foxml, BinaryEditor.FULL_ID, mediaType).write(f, 0, null);

        start = System.nanoTime();
        targetName = String.format("%s.preview.%s", originalFilename, imageType.getDefaultFileExtension());
        f = writeImage(scale(tiff, null, 1000), tempBatchFolder, targetName, imageType);
        long endPreview = System.nanoTime() - start;
        BinaryEditor.dissemination(foxml, BinaryEditor.PREVIEW_ID, mediaType).write(f, 0, null);

        start = System.nanoTime();
        targetName = String.format("%s.thumb.%s", originalFilename, imageType.getDefaultFileExtension());
        f = writeImage(scale(tiff, 120, 128), tempBatchFolder, targetName, imageType);
        long endThumb = System.nanoTime() - start;
        BinaryEditor.dissemination(foxml, BinaryEditor.THUMB_ID, mediaType).write(f, 0, null);

        LOG.info(String.format("file: %s, read: %s, full: %s, preview: %s, thumb: %s",
                originalFilename, endRead / 1000000, endFull / 1000000, endPreview / 1000000, endThumb / 1000000));
    }

    private static File writeImage(BufferedImage image, File folder, String filename, ImageMimeType imageType) throws IOException {
        File imgFile = new File(folder, filename);
        FileImageOutputStream fos = new FileImageOutputStream(imgFile);
        try {
            ImageSupport.writeImageToStream(image, imageType.getDefaultFileExtension(), fos, 1.0f);
            return imgFile;
        } finally {
            fos.close();
        }
    }

    private static BufferedImage scale(BufferedImage tiff, Integer maxWidth, Integer maxHeight) {
        long start = System.nanoTime();
        int height = tiff.getHeight();
        int width = tiff.getWidth();
        int targetWidth = width;
        int targetHeight = height;
        double scale = Double.MAX_VALUE;
        if (maxHeight != null && height > maxHeight) {
            scale = (double) maxHeight / height;
        }
        if (maxWidth != null && width > maxWidth) {
            double scalew = (double) maxWidth / width;
            scale = Math.min(scale, scalew);
        }
        if (scale != Double.MAX_VALUE) {
            targetHeight = (int) (height * scale);
            targetWidth = (int) (width * scale);
        }
        BufferedImage scaled = ImageSupport.scale(tiff, targetWidth, targetHeight);
        LOG.info(String.format("scaled [%s, %s] to [%s, %s], boundary [%s, %s] [w, h], time: %s ms",
                width, height, targetWidth, targetHeight, maxWidth, maxHeight, (System.nanoTime() - start) / 1000000));
        return scaled;
    }

    private void copyFile(File src, File dst) throws IOException {
        FileChannel srcChannel = null;
        FileChannel dstChannel = null;
        FileInputStream srcStream = null;
        FileOutputStream dstStream = null;
        boolean done = false;
        try {
            srcStream = new FileInputStream(src);
            dstStream = new FileOutputStream(dst);
            srcChannel = srcStream.getChannel();
            dstChannel = dstStream.getChannel();
            long count = 0;
            long length = src.length();
            while ((count += dstChannel.transferFrom(srcChannel, count, length - count)) < length) {
                // no op
            }
            done = true;
        } finally {
            closeQuietly(srcChannel, src.toString(), done);
            closeQuietly(srcStream, src.toString(), done);
            closeQuietly(dstChannel, dst.toString(), done);
            closeQuietly(dstStream, dst.toString(), done);
        }
    }

    private static void closeQuietly(Closeable c, String description, boolean notQuietly) throws IOException {
        if (c != null) {
            try {
                c.close();
            } catch (IOException ex) {
                if (notQuietly) {
                    throw ex;
                }
                LOG.log(Level.SEVERE, description, ex);
            }
        }
    }
}