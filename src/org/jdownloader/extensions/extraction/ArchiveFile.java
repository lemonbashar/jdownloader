package org.jdownloader.extensions.extraction;

import java.awt.Color;

import org.jdownloader.controlling.FileCreationManager;

public interface ArchiveFile {

    public Boolean isComplete();

    public String getFilePath();

    public long getFileSize();

    public void deleteFile(FileCreationManager.DeleteOption option);

    public boolean exists();

    public void invalidateExists();

    public String getName();

    public void setStatus(ExtractionController controller, ExtractionStatus error);

    public void setMessage(ExtractionController controller, String plugins_optional_extraction_status_notenoughspace);

    public void setProgress(ExtractionController controller, long value, long max, Color color);

    public void removePluginProgress(ExtractionController controller);

    public void onCleanedUp(ExtractionController controller);

    public void setArchive(Archive archive);

    public void setPartOfAnArchive(Boolean b);

    public Boolean isPartOfAnArchive();

    public void notifyChanges(Object type);

}
