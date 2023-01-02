package io.vanillabp.springboot.adapter;

import io.vanillabp.spi.service.MultiInstanceElementResolver;

public class MultiInstance<E> implements MultiInstanceElementResolver.MultiInstance<E> {

    private final E element;

    private final int total;

    private final int index;

    public MultiInstance(
            final E element,
            final int total,
            final int index) {

        this.element = element;
        this.total = total;
        this.index = index;
    }

    @Override
    public E getElement() {

        return element;

    }

    @Override
    public int getTotal() {

        return total;

    }

    @Override
    public int getIndex() {

        return index;

    }

}
