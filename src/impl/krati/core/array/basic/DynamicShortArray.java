package krati.core.array.basic;

import java.io.File;
import java.io.IOException;

import krati.array.DynamicArray;
import krati.array.ShortArray;
import krati.core.array.entry.EntryShortFactory;
import krati.core.array.entry.EntryValueShort;

import org.apache.log4j.Logger;

/**
 * DynamicShortArray
 * 
 * @author jwu
 *
 */
public class DynamicShortArray extends AbstractRecoverableArray<EntryValueShort> implements ShortArray, DynamicArray, ArrayExpandListener
{
    private final static int _subArrayBits = 16;
    private final static int _subArraySize = 1 << _subArrayBits;
    private final static Logger _log = Logger.getLogger(DynamicShortArray.class);
    private MemoryShortArray _internalArray;
    
    public DynamicShortArray(int entrySize, int maxEntries, File directory) throws Exception
    {
        super(_subArraySize /* initial array length and subArray length */, 2, entrySize, maxEntries, directory, new EntryShortFactory());
    }
    
    @Override
    protected void loadArrayFileData()
    {
        long maxScn = _arrayFile.getLwmScn();
        
        try
        {
            _internalArray = new MemoryShortArray(_subArrayBits);
            _arrayFile.load(_internalArray);
            
            expandCapacity(_internalArray.length() - 1);
            _internalArray.setArrayExpandListener(this);
        }
        catch(Exception e)
        {
            maxScn = 0;
            clear();
        }
        
        _entryManager.setWaterMarks(maxScn, maxScn);
    }
    
    /**
     * Sync-up the high water mark to a given value.
     * 
     * @param endOfPeriod
     */
    @Override
    public void saveHWMark(long endOfPeriod)
    {
        if (getHWMark() < endOfPeriod)
        {
            try
            {
                set(0, get(0), endOfPeriod);
            }
            catch(Exception e)
            {
                _log.error(e);
            }
        }
    }
    
    @Override
    public void clear()
    {
        if (_internalArray != null)
        {
            _internalArray.clear();
        }
        
        // Clear the entry manager
        _entryManager.clear();
        
        // Clear the underly array file
        try
        {
            _arrayFile.reset(_internalArray, _entryManager.getLWMark());
        }
        catch(IOException e)
        {
            _log.error(e.getMessage(), e);
        }
    }
    
    @Override
    public short get(int index)
    {
        return _internalArray.get(index);
    }
    
    @Override
    public void set(int index, short value, long scn) throws Exception
    {
        _internalArray.set(index, value);
        _entryManager.addToPreFillEntryShort(index, value, scn);
    }
    
    @Override
    public short[] getInternalArray()
    {
        return _internalArray.getInternalArray();
    }

    @Override
    public void expandCapacity(int index) throws Exception
    {
        if(index < _length) return;
        
        int newLength = ((index >> _subArrayBits) + 1) * _subArraySize;

        // Expand internal array in memory 
        if(_internalArray.length() < newLength)
        {
            _internalArray.expandCapacity(index);
        }
        
        // Expand array file on disk
        _arrayFile.setArrayLength(newLength, null /* do not rename */);
        
        // Reset _length
        _length = newLength;
        
        // Add to logging
        _log.info("Expanded: _length=" + _length);
    }
    
    @Override
    public void arrayExpanded(DynamicArray dynArray)
    {
        if(dynArray == _internalArray)
        {
            try
            {
                expandCapacity(dynArray.length() - 1);
            }
            catch(Exception e)
            {
                _log.error("Failed to expand: " + dynArray.length());
            }
        }
    }
    
    public final int subArrayLength()
    {
        return _subArraySize;
    }
}
