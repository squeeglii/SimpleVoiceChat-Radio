package de.maxhenkel.radio.utils;

public class CircularShortBuffer {

    private short[] buffer;
    private int position;
    private int size;

    public CircularShortBuffer(int capacity) {
        this.buffer = new short[capacity];
        this.position = 0;
        this.size = 0;
    }

    public void add(short[] data, int offset, int length) {
        if (length > getFreeSpace()) {
            throw new IndexOutOfBoundsException("Not enough space in buffer");
        }

        for (int i = offset; i < offset + length; i++) {
            buffer[position] = data[i];
            position = (position + 1) % maxCapacity();
        }

        size = Math.min(size + length, maxCapacity());
    }

    public void add(short[] data) {
        add(data, 0, data.length);
    }

    public int get(short[] dest, int offset, int length) {
        int elementsToRead = Math.min(length, size);

        for (int i = 0; i < elementsToRead; i++) {
            dest[offset + i] = buffer[(position - size + maxCapacity()) % maxCapacity()];
            size--;
        }

        return elementsToRead;
    }

    public int get(short[] dest) {
        return get(dest, 0, dest.length);
    }

    public int peek(short[] dest, int offset, int length) {
        int fakeSize = size;
        int elementsToRead = Math.min(length, fakeSize);

        for (int i = 0; i < elementsToRead; i++) {
            dest[offset + i] = buffer[(position - fakeSize + maxCapacity()) % maxCapacity()];
            fakeSize--;
        }

        return elementsToRead;
    }

    public int peek(short[] dest) {
        return peek(dest, 0, dest.length);
    }

    public void skip(int length) {
        size = Math.max(size - length, 0);
    }

    public void clear() {
        position = 0;
        size = 0;
    }

    public int getFreeSpace() {
        return maxCapacity() - size;
    }

    public int maxCapacity() {
        return buffer.length;
    }

    public int sizeUsed() {
        return size;
    }

    public boolean hasRemaining() {
        return sizeUsed() > 0;
    }

}
