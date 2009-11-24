/*
 * Copyright (c) 2005-2009 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.api.util;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.security.Crypt;

import java.io.*;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import junit.framework.Test;
import junit.framework.TestSuite;

/**
 * User: jeckels
 * Date: Dec 5, 2005
 */
public class FileUtil
{
    public static boolean deleteDirectoryContents(File dir)
    {
        if (dir.isDirectory())
        {
            String[] children = dir.list();
            for (String aChildren : children)
            {
                boolean success = deleteDir(new File(dir, aChildren));
                if (!success)
                {
                    return false;
                }
            }
        }
        return true;
    }

    public static boolean deleteSubDirs(File dir)
    {
        if (dir.isDirectory())
        {
            String[] children = dir.list();
            for (String aChildren : children)
            {
                boolean success = true;
                File child = new File(dir, aChildren);
                if (child.isDirectory())
                    success = deleteDir(child);
                if (!success)
                {
                    return false;
                }
            }
        }
        return true;
    }

    /** File.delete() will only delete a directory if it's empty, but this will
     * delete all the contents and the directory */
    public static boolean deleteDir(File dir)
    {
        boolean success = deleteDirectoryContents(dir);
        if (!success)
        {
            return false;
        }

        // The directory is now empty so delete it
        return dir.delete();
    }

    /**
     * Remove text right of a specific number of periods, including the periods, from a file's name.
     * <ul>
     *  <li>C:\dir\name.ext, 1 => name</li>
     *  <li>C:\dir\name.ext1.ext2, 2 => name</li>
     *  <li>C:\dir\name.ext1.ext2, 1 => name.ext1</li>
     * </ul>
     *
     * @param file file from which to get the name
     * @param dots number of dots to remove
     * @return base name
     */
    public static String getBaseName(File file, int dots)
    {
        String baseName = file.getName();
        while (dots-- > 0 && baseName.indexOf('.') != -1)
            baseName = baseName.substring(0, baseName.lastIndexOf('.'));
        return baseName;
    }

    /**
     * Remove text right of and including the last period in a file's name.
     * @param file file from which to get the name
     * @return base name
     */
    public static String getBaseName(File file)
    {
        return getBaseName(file, 1);
    }

    /**
     * Returns the file name extension without the dot, null if there
     * isn't one.
     * @param file
     * @return
     */
    public static String getExtension(File file)
    {
        String name = file.getName();
        if (name.lastIndexOf('.') != -1)
        {
            return name.substring(name.lastIndexOf('.') + 1, name.length());
        }
        return null;
    }

    /**
     * Get relative path of File 'file' with respect to 'home' directory
     * <p><pre>
     * example : home = /a/b/c
     *           file    = /a/d/e/x.txt
     *           return = ../../d/e/x.txt
     * </pre><p>
     * The path returned has system specific directory separators.
     * <p>
     * It is equivalent to:<br>
     * <pre>home.toURI().relativize(f.toURI).toString().replace('/', File.separatorChar)</pre>
     *
     * @param home base path, should be a directory, not a file, or it doesn't make sense
     * @param file    file to generate path for
     * @param canonicalize whether or not the paths need to be canonicalized
     * @return path from home to file as a string
     */
    public static String relativize(File home, File file, boolean canonicalize) throws IOException
    {
        if (canonicalize)
        {
            home = home.getCanonicalFile();
            file = file.getCanonicalFile();
        }
        return matchPathLists(getPathList(home), getPathList(file));
    }

    /**
     * Get a relative path of File 'file' with respect to 'home' directory,
     * forcing Unix (i.e. URI) forward slashes for directory separators.
     * <p>
     * This is a lot like <cod>URIUtil.relativize()</code> without requiring
     * that the file be a descendant of the base.
     * <p>
     * It is equivalent to:<br>
     * <pre>home.toURI().relativize(f.toURI).toString()</pre>
     */
    public static String relativizeUnix(File home, File f, boolean canonicalize) throws IOException
    {
        return relativize(home, f, canonicalize).replace('\\', '/');
    }


    /**
     * Break a path down into individual elements and add to a list.
     * <p/>
     * example : if a path is /a/b/c/d.txt, the breakdown will be [d.txt,c,b,a]
     *
     * @param file input file
     * @return a List collection with the individual elements of the path in reverse order
     */
    private static List<String> getPathList(File file) throws IOException
    {
        List<String> parts = new ArrayList<String>();
        while (file != null)
        {
            parts.add(file.getName());
            file = file.getParentFile();
        }

        return parts;
    }


    /**
     * Figure out a string representing the relative path of
     * 'file' with respect to 'home'
     *
     * @param home home path
     * @param file path of file
     * @return relative path from home to file
     */
    public static String matchPathLists(List<String> home, List<String> file)
    {
        // start at the beginning of the lists
        // iterate while both lists are equal
        StringBuffer path = new StringBuffer();
        int i = home.size() - 1;
        int j = file.size() - 1;

        // first eliminate common root
        while ((i >= 0) && (j >= 0) && (home.get(i).equals(file.get(j))))
        {
            i--;
            j--;
        }

        // for each remaining level in the home path, add a ..
        for (; i >= 0; i--)
            path.append("..").append(File.separator);

        // for each level in the file path, add the path
        for (; j >= 1; j--)
            path.append(file.get(j)).append(File.separator);

        // if nothing left of the file, then it was a directory
        // of which home is a subdirectory.
        if (j < 0)
        {
            if (path.length() == 0)
                path.append(".");
            else
                path.delete(path.length() - 1, path.length());  // remove trailing sep
        }
        else
            path.append(file.get(j));   // add file name

        return path.toString();
    }


    // FileUtil.copyFile() does not use transferTo() or sync()
    public static void copyFile(File src, File dst) throws IOException
    {
        dst.createNewFile();
        FileInputStream is = null;
        FileOutputStream os = null;
        FileChannel in = null;
        FileLock lockIn = null;
        FileChannel out = null;
        FileLock lockOut = null;
        try
        {
            is = new FileInputStream(src);
            in = is.getChannel();
            lockIn = in.lock(0L, Long.MAX_VALUE, true);
            os = new FileOutputStream(dst); 
            out = os.getChannel();
            lockOut = out.lock();
            in.transferTo(0, in.size(), out);
            os.getFD().sync();
            dst.setLastModified(src.lastModified());
        }
        finally
        {
            if (null != lockIn)
                lockIn.release();
            if (null != lockOut)
                lockOut.release();
            if (null != in)
                in.close();
            if (null != out)
                out.close();
            if (null != os)
                os.close();
            if (null != is)
                is.close();
        }
    }

    /**
     * Copies an entire file system branch to another location, including the root directory itself
     * @param src The source file root
     * @param dest The destination file root
     * @throws IOException thrown from IO functions
     */
    public static void copyBranch(File src, File dest) throws IOException
    {
        copyBranch(src, dest, false);
    }

    public static void copyBranch(File src, File dest, boolean contentsOnly) throws IOException
    {
        copyBranch(src, dest, contentsOnly, null);
    }

    /**
     * Copies an entire file system branch to another location
     * @param src The source file root
     * @param dest The destination file root
     * @param contentsOnly Pass false to copy the root directory as well as the files within; true to just copy the contents
     * @param listener An optional FileCopyListener that should be notified (or null if not desired)
     * @throws IOException Thrown if there's an IO exception
     */
    public static void copyBranch(File src, File dest, boolean contentsOnly, @Nullable FileCopyListener listener) throws IOException
    {
        //if src is just a file, copy it and return
        if(src.isFile())
        {
            File destFile = new File(dest, src.getName());
            if(null == listener || listener.shouldCopy(src, destFile))
            {
                copyFile(src, destFile);
                if(null != listener)
                    listener.afterFileCopy(src, destFile);
            }
            return;
        }

        //if copying the src root directory as well, make that
        //within the dest and re-assign dest to the new directory
        if(!contentsOnly)
        {
            dest = new File(dest, src.getName());
            if(null == listener || listener.shouldCopy(src, dest))
            {
                dest.mkdirs();
                if(!dest.isDirectory())
                    throw new IOException("Unable to create the directory " + dest.toString() + "!");
                if(null != listener)
                    listener.afterFileCopy(src, dest);
            }
        }

        for(File file : src.listFiles())
        {
            copyBranch(file, dest, false, listener);
        }
    }

    /**
     * always returns path starting with /.  Tries to leave trailing '/' as is
     * (unless ends with /. or /..)
     * 
     * @param path path to normalize
     * @return cleaned path or null if path goes outside of 'root'
     * @deprecated use java.util.Path
     */
    public static String normalize(String path)
    {
        if (path == null || equals(path,'/'))
            return path;

        String str = path;
        if (str.indexOf('\\') >= 0)
            str = str.replace('\\', '/');
        if (!startsWith(str,'/'))
            str = "/" + str;
        int len = str.length();

        // quick scan, look for /. or //
quickScan:
        {
            for (int i=0 ; i<len-1 ; i++)
            {
                char c0 = str.charAt(i);
                if (c0 != '/') continue;
                char c1 = str.charAt(i+1);
                if (c1 == '.' || c1 == '/')
                    break quickScan;
                i++;    //already looked at c1
            }
            return str;
        }

        ArrayList<String> list = normalizeSplit(str);
        if (null == list)
            return null;
        if (list.isEmpty())
            return "/";
        StringBuilder sb = new StringBuilder(str.length()+2);
        for (String name : list)
        {
            sb.append('/');
            sb.append(name);
        }
        return sb.toString();
    }


    /** @deprecated use java.util.Path */
    public static ArrayList<String> normalizeSplit(String str)
    {
        int len = str.length();
        ArrayList<String> list = new ArrayList<String>();
        int start = 0;
        for (int i=0 ; i<=len ; i++)
        {
            if (i==len || str.charAt(i) == '/')
            {
                if (start < i)
                {
                    String part = str.substring(start, i);
                    if (part.length()==0 || equals(part,'.'))
                    {
                    }
                    else if (part.equals(".."))
                    {
                        if (list.isEmpty())
                            return null;
                        list.remove(list.size()-1);
                    }
                    else
                    {
                        list.add(part);
                    }
                }
                start=i+1;
            }
        }
        return list;
    }


    static boolean startsWith(String s, char ch)
    {
        return s.length() > 0 && s.charAt(0) == ch;
    }

    static boolean endsWith(String s, char ch)
    {
        return s.length() > 0 && s.charAt(s.length()-1) == ch;
    }

    static boolean equals(String s, char ch)
    {
        return s.length() == 1 && s.charAt(0) == ch;
    }

    public static String relativePath(String dir, String filePath)
    {
        dir = normalize(dir);
        filePath = normalize(filePath);
        if (dir.endsWith("/"))
            dir = dir.substring(0,dir.length()-1);
        if (!filePath.toLowerCase().startsWith(dir.toLowerCase()))
            return null;
        String relPath = filePath.substring(dir.length());
        if (relPath.length() == 0)
            return relPath;
        if (relPath.startsWith("/"))
            return relPath.substring(1);
        return null;
    }


    public static String md5sum(InputStream is) throws IOException
    {
        DigestInputStream dis = null;
        try
        {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            dis = new DigestInputStream(is, digest);
            byte[] buf = new byte[8*1024];
            while (-1 != (dis.read(buf)))
            {
               /* */
            }
            return Crypt.encodeHex(digest.digest());
        }
        catch (NoSuchAlgorithmException e)
        {
            Logger.getInstance(FileUtil.class).error("unexpected error", e);
            return null;
        }
        finally
        {
            IOUtils.closeQuietly(dis);
            IOUtils.closeQuietly(is);
        }
    }
    
    public static String md5sum(File file) throws IOException
    {
        return md5sum(new FileInputStream(file));
    }


    //
    //  NOTE: IOUtil uses fairly small buffers for copy
    //
    
    final static int BUFFERSIZE = 32*1024;

    /** Does not close input or output stream */
    public static long copyData(InputStream is, OutputStream os) throws IOException
    {
        byte[] buf = new byte[BUFFERSIZE];
        long total = 0;
        int r;
        while (0 <= (r = is.read(buf)))
        {
            os.write(buf,0,r);
            total += r;
        }
        return total;
    }


    /** Does not close input or output stream */
    public static void copyData(InputStream is, DataOutput os, long len) throws IOException
    {
        byte[] buf = new byte[BUFFERSIZE];
        long remaining = len;
        do
        {
            int r = (int)Math.min(buf.length, remaining);
            r = is.read(buf, 0, r);
            os.write(buf,0,r);
            remaining -= r;
        } while (0 < remaining);
    }


    /** Does not close input or output stream */
    public static void copyData(InputStream is, DataOutput os) throws IOException
    {
        byte[] buf = new byte[BUFFERSIZE];
        int r;
        while (0 < (r = is.read(buf)))
            os.write(buf,0,r);
    }

    public static File canonicalFile(URI uri)
    {
        return canonicalFile(new File(uri));
    }


    public static File canonicalFile(String path)
    {
        return canonicalFile(new File(path));
    }


    public static File canonicalFile(File f)
    {
        try
        {
            return f.getCanonicalFile();
        }
        catch (IOException x)
        {
            return f;
        }
    }

    private static char[] illegalChars = {'/','\\',':','?','<','>','*','|','"','^'};

    public static String makeLegalName(String name)
    {
        //limit to 255 chars (FAT and OS X)
        //replace illegal chars
        //can't end with space (windows)
        //can't end with period (windows)
        char[] ret = new char[Math.min(255, name.length())];
        for(int idx = 0; idx < ret.length; ++idx)
        {
            char ch = name.charAt(idx);
            if(ch == '/' || ch == '?' || ch == '<' || ch == '>' || ch == '\\' || ch == ':'
                    || ch == '*' || ch == '|' || ch == '"' || ch == '^')
            {
                ch = '_';
            }

            if(idx == name.length() - 1 && (ch == ' ' || ch == '.'))
                ch = '_';

            ret[idx] = ch;
        }
        return new String(ret);
    }

    /**
     * Returns the absolute path to a file. On Windows and Mac, corrects casing in file paths to match the
     * canonical path.
     */
    public static File getAbsoluteCaseSensitiveFile(File file)
    {
        file = resolveFile(file.getAbsoluteFile());
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.startsWith("windows") || osName.startsWith("mac os"))
        {
            try
            {
                File canonicalFile = file.getCanonicalFile();
                if (canonicalFile.getAbsolutePath().equalsIgnoreCase(file.getAbsolutePath()))
                {
                    return canonicalFile;
                }
            }
            catch (IOException e)
            {
                // Ignore and just use the absolute file
            }
        }
        return file.getAbsoluteFile();
    }

    /**
     * Strips out ".." and "." from the path
     */
    private static File resolveFile(File file)
    {
        File parent = file.getParentFile();
        if (parent == null)
        {
            return file;
        }
        if (".".equals(file.getName()))
        {
            return resolveFile(parent);
        }
        int dotDotCount = 0;
        while ("..".equals(file.getName()) || dotDotCount > 0)
        {
            if ("..".equals(file.getName()))
            {
                dotDotCount++;
            }
            else if (!".".equals(file.getName()))
            {
                dotDotCount--;
            }
            if (parent.getParentFile() == null)
            {
                return parent;
            }
            file = file.getParentFile();
            parent = file.getParentFile();
        }
        return new File(resolveFile(parent), file.getName());
    }


    public static class TestCase extends junit.framework.TestCase
    {
        private static final File ROOT;

        static
        {
            File f = new File(".").getAbsoluteFile();
            while (f.getParentFile() != null)
            {
                f = f.getParentFile();
            }
            ROOT = f;
        }

        public void testStandardResolve()
        {
            assertEquals(new File(ROOT, "test/path/sub"), resolveFile(new File(ROOT, "test/path/sub")));
            assertEquals(new File(ROOT, "test"), resolveFile(new File(ROOT, "test")));
            assertEquals(new File(ROOT, "test/path/file.ext"), resolveFile(new File(ROOT, "test/path/file.ext")));
        }

        public void testDotResolve()
        {
            assertEquals(new File(ROOT, "test/path/sub"), resolveFile(new File(ROOT, "test/path/./sub")));
            assertEquals(new File(ROOT, "test"), resolveFile(new File(ROOT, "./test")));
            assertEquals(new File(ROOT, "test/path/file.ext"), resolveFile(new File(ROOT, "test/path/file.ext/.")));
        }

        public void testDotDotResolve()
        {
            assertEquals(ROOT, resolveFile(new File(ROOT, "..")));
            assertEquals(new File(ROOT, "test/sub"), resolveFile(new File(ROOT, "test/path/../sub")));
            assertEquals(new File(ROOT, "test/sub2"), resolveFile(new File(ROOT, "test/path/../sub/../sub2")));
            assertEquals(new File(ROOT, "test"), resolveFile(new File(ROOT, "test/path/sub/../..")));
            assertEquals(new File(ROOT, "sub"), resolveFile(new File(ROOT, "test/path/../../sub")));
            assertEquals(new File(ROOT, "sub2"), resolveFile(new File(ROOT, "test/path/../../sub/../sub2")));
            assertEquals(new File(ROOT, "sub2"), resolveFile(new File(ROOT, "test/path/.././../sub/../sub2")));
            assertEquals(new File(ROOT, "sub2"), resolveFile(new File(ROOT, "test/path/.././../sub/../../sub2")));
            assertEquals(new File(ROOT, "sub2"), resolveFile(new File(ROOT, "a/test/path/.././../sub/../../sub2")));
            assertEquals(new File(ROOT, "b/sub2"), resolveFile(new File(ROOT, "b/a/test/path/.././../sub/../../sub2")));
            assertEquals(ROOT, resolveFile(new File(ROOT, "test/path/../../../..")));
            assertEquals(new File(ROOT, "test/sub"), resolveFile(new File(ROOT, "../../../../test/sub")));
            assertEquals(new File(ROOT, "test"), resolveFile(new File(ROOT, "../test")));
            assertEquals(new File(ROOT, "test/path"), resolveFile(new File(ROOT, "test/path/file.ext/..")));
            assertEquals(new File(ROOT, "folder"), resolveFile(new File(ROOT, ".././../folder")));
            assertEquals(new File(ROOT, "b"), resolveFile(new File(ROOT, "folder/a/.././../b")));
        }

        public static Test suite()
        {
            return new TestSuite(TestCase.class);
        }
    }
}
