package com.example.demobase.service;

import com.example.demobase.dto.WordDTO;
import com.example.demobase.model.Word;
import com.example.demobase.repository.WordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WordServiceTest {

    @Mock
    private WordRepository wordRepository;

    @InjectMocks
    private WordService wordService;

    private Word word1;
    private Word word2;
    private Word word3;

    @BeforeEach
    void setUp() {
        word1 = new Word(1L, "PROGRAMADOR", true);
        word2 = new Word(2L, "COMPUTADORA", false);
        word3 = new Word(3L, "TECNOLOGIA", false);
    }

    @Test
    void testGetAllWords() {
        // TODO: Implementar el test para getAllWords

        // Given
        List<Word> words = Arrays.asList(word1, word2, word3);
        when(wordRepository.findAllOrdered()).thenReturn(words);


        List<WordDTO> result = wordService.getAllWords();


        assertNotNull(result);
        assertEquals(3, result.size());


        WordDTO wordDto1 = result.get(0);
        assertEquals(1L, wordDto1.getId());
        assertEquals("PROGRAMADOR", wordDto1.getPalabra());
        assertTrue(wordDto1.getUtilizada());


        WordDTO wordDto2 = result.get(1);
        assertEquals(2L, wordDto2.getId());
        assertEquals("COMPUTADORA", wordDto2.getPalabra());
        assertFalse(wordDto2.getUtilizada());


        WordDTO wordDto3 = result.get(2);
        assertEquals(3L, wordDto3.getId());
        assertEquals("TECNOLOGIA", wordDto3.getPalabra());
        assertFalse(wordDto3.getUtilizada());


        verify(wordRepository, times(1)).findAllOrdered();
        
    }

    @Test
    void testGetAllWords_EmptyList() {
        // TODO: Implementar el test para getAllWords_EmptyList

        List<Word> emptyList = Arrays.asList();
        when(wordRepository.findAllOrdered()).thenReturn(emptyList);


        List<WordDTO> result = wordService.getAllWords();


        assertNotNull(result);
        assertEquals(0, result.size());
        assertTrue(result.isEmpty());


        verify(wordRepository, times(1)).findAllOrdered();
        
    }
}

