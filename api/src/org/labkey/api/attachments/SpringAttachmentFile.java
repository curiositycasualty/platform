package org.labkey.api.attachments;

import org.springframework.web.multipart.MultipartFile;
import org.apache.struts.upload.CommonsMultipartRequestHandler;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

/**
 * User: adam
 * Date: Sep 10, 2007
 * Time: 7:30:27 PM
 */
public class SpringAttachmentFile implements AttachmentFile
{
    private MultipartFile _file;
    private InputStream _in;
    private String _filename = null;

    public SpringAttachmentFile(MultipartFile file)
    {
        _file = file;
    }

    public String getFilename()
    {
        return null != _filename ? _filename : _file.getOriginalFilename();
    }

    public void setFilename(String filename)
    {
        _filename = filename;
    }

    public String getContentType()
    {
        return _file.getContentType();
    }

    public long getSize()
    {
        return _file.getSize();
    }

    // TODO: this was null == _file || null == formFile.getFilename() || 0 == formFile.getFilename().length());

    public String getError()
    {
        if (!_file.isEmpty() && 0 == getSize())
            return "Warning: " + getFilename() + " was not uploaded.  Attachments must not exceed the maximum file size of " + getMaxFileUploadSize() + ".";
        else
            return null;
    }

    private String getMaxFileUploadSize()
    {
        // For now, assume we're configured for the default maximum struts file size
        // TODO: Lame -- should query Spring to determine the configured max file size
        return (CommonsMultipartRequestHandler.DEFAULT_SIZE_MAX / (1024 * 1024)) + "MB";
    }

    public byte[] getBytes() throws IOException
    {
        return _file.getBytes();
    }

    public InputStream openInputStream() throws IOException
    {
        _in = _file.getInputStream();
        return _in;
    }

    public void closeInputStream() throws IOException
    {
        if (null != _in)
            _in.close();
    }

    public static List<AttachmentFile> createList(Map<String, MultipartFile> fileMap)
    {
        List<AttachmentFile> files = new ArrayList<AttachmentFile>(fileMap.size());

        for (MultipartFile file : fileMap.values())
            if (!file.isEmpty())
                files.add(new SpringAttachmentFile(file));

        return files;
    }

    public String toString()
    {
        return getFilename();
    }

    public boolean isEmpty()
    {
        return _file.isEmpty();
    }
}
