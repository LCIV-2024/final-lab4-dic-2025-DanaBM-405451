package com.example.demobase.service;

import com.example.demobase.dto.GameDTO;
import com.example.demobase.dto.GameResponseDTO;
import com.example.demobase.model.Game;
import com.example.demobase.model.GameInProgress;
import com.example.demobase.model.Player;
import com.example.demobase.model.Word;
import com.example.demobase.repository.GameInProgressRepository;
import com.example.demobase.repository.GameRepository;
import com.example.demobase.repository.PlayerRepository;
import com.example.demobase.repository.WordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GameService {
    
    private final GameRepository gameRepository;
    private final GameInProgressRepository gameInProgressRepository;
    private final PlayerRepository playerRepository;
    private final WordRepository wordRepository;
    
    private static final int MAX_INTENTOS = 7;
    private static final int PUNTOS_PALABRA_COMPLETA = 20;
    private static final int PUNTOS_POR_LETRA = 1;
    
    @Transactional
    public GameResponseDTO startGame(Long playerId) {
        GameResponseDTO response = new GameResponseDTO();
        // TODO: Implementar el método startGame
        //  Validar que el jugador existe
        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new RuntimeException("Jugador no encontrado con ID: " + playerId));

        //  Buscar una palabra aleatoria no utilizada usando el método del repositorio
        Optional<Word> palabraOpcional = wordRepository.findRandomWord();

        if (palabraOpcional.isEmpty()) {
            throw new RuntimeException("No hay palabras disponibles para jugar");
        }

        Word palabraSeleccionada = palabraOpcional.get();

        //  Verificar si ya existe una partida en curso para este jugador con esta palabra
        Optional<GameInProgress> partidaExistente = gameInProgressRepository
                .findByJugadorAndPalabra(playerId, palabraSeleccionada.getId());

        if (partidaExistente.isPresent()) {
            // Si ya existe una partida en curso, retornar su estado actual
            return buildResponseFromGameInProgress(partidaExistente.get());
        }

        // Marcar la palabra como utilizada
        palabraSeleccionada.setUtilizada(true);
        wordRepository.save(palabraSeleccionada);

        // Crear nueva partida en curso
        GameInProgress nuevaPartida = new GameInProgress();
        nuevaPartida.setJugador(player);
        nuevaPartida.setPalabra(palabraSeleccionada);
        nuevaPartida.setLetrasIntentadas(""); // Inicialmente sin letras intentadas
        nuevaPartida.setIntentosRestantes(MAX_INTENTOS); // 7 intentos disponibles
        nuevaPartida.setFechaInicio(LocalDateTime.now());

        gameInProgressRepository.save(nuevaPartida);

        //  Construir respuesta inicial
        String palabraOculta = "_".repeat(palabraSeleccionada.getPalabra().length());

        response.setPalabraOculta(palabraOculta);
        response.setLetrasIntentadas(new ArrayList<>());
        response.setIntentosRestantes(MAX_INTENTOS);
        response.setPalabraCompleta(false);
        response.setPuntajeAcumulado(0);

        return response;
    }
    
    @Transactional
    public GameResponseDTO makeGuess(Long playerId, Character letra) {
        GameResponseDTO response = new GameResponseDTO();
        // TODO: Implementar el método makeGuess
        //Validar que el jugador existe
        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new RuntimeException("Jugador no encontrado con ID: " + playerId));

        //  Convertir la letra a mayúscula
        Character letraMayuscula = Character.toUpperCase(letra);

        //  Buscar la partida en curso más reciente del jugador (ordenada por fecha descendente)
        List<GameInProgress> partidasEnCurso = gameInProgressRepository
                .findByJugadorIdOrderByFechaInicioDesc(playerId);

        if (partidasEnCurso.isEmpty()) {
            throw new RuntimeException("No hay partida en curso para este jugador. Inicia una nueva con /api/games/start/" + playerId);
        }

        //  Tomar la partida más reciente (la primera de la lista ya está ordenada DESC)
        GameInProgress partidaActual = partidasEnCurso.get(0);

        //  Obtener letras ya intentadas
        Set<Character> letrasIntentadas = stringToCharSet(partidaActual.getLetrasIntentadas());

        //  Verificar si la letra ya fue intentada
        if (letrasIntentadas.contains(letraMayuscula)) {
            // Si ya fue intentada, retornar el estado actual sin cambios
            return buildResponseFromGameInProgress(partidaActual);
        }

        //  Agregar la nueva letra
        letrasIntentadas.add(letraMayuscula);

        // Verificar si la letra está en la palabra
        String palabraCompleta = partidaActual.getPalabra().getPalabra().toUpperCase();
        boolean letraCorrecta = palabraCompleta.indexOf(letraMayuscula) >= 0;


        if (!letraCorrecta) {
            partidaActual.setIntentosRestantes(partidaActual.getIntentosRestantes() - 1);
        }

        // Actualizar las letras intentadas en la partida
        partidaActual.setLetrasIntentadas(charSetToString(letrasIntentadas));

        //  Generar palabra oculta
        String palabraOculta = generateHiddenWord(palabraCompleta, letrasIntentadas);
        boolean esPalabraCompleta = palabraOculta.equals(palabraCompleta);

        //  Guardar el estado actualizado
        gameInProgressRepository.save(partidaActual);

        //  Verificar si el juego terminó
        boolean juegoTerminado = esPalabraCompleta || partidaActual.getIntentosRestantes() == 0;

        if (juegoTerminado) {
            // Calcular puntaje final
            int puntajeFinal = calculateScore(palabraCompleta, letrasIntentadas, esPalabraCompleta, partidaActual.getIntentosRestantes());

            // Guardar la partida en el historial
            saveGame(player, partidaActual.getPalabra(), esPalabraCompleta, puntajeFinal);

            // Eliminar de partidas en curso
            gameInProgressRepository.delete(partidaActual);
        }

        //Construir respuesta
        return buildResponseFromGameInProgress(partidaActual);
    }
    
    private GameResponseDTO buildResponseFromGameInProgress(GameInProgress gameInProgress) {
        String palabra = gameInProgress.getPalabra().getPalabra().toUpperCase();
        Set<Character> letrasIntentadas = stringToCharSet(gameInProgress.getLetrasIntentadas());
        String palabraOculta = generateHiddenWord(palabra, letrasIntentadas);
        boolean palabraCompleta = palabraOculta.equals(palabra);
        
        GameResponseDTO response = new GameResponseDTO();
        response.setPalabraOculta(palabraOculta);
        response.setLetrasIntentadas(new ArrayList<>(letrasIntentadas));
        response.setIntentosRestantes(gameInProgress.getIntentosRestantes());
        response.setPalabraCompleta(palabraCompleta);
        
        int puntaje = calculateScore(palabra, letrasIntentadas, palabraCompleta, gameInProgress.getIntentosRestantes());
        response.setPuntajeAcumulado(puntaje);
        
        return response;
    }
    
    private Set<Character> stringToCharSet(String str) {
        Set<Character> set = new HashSet<>();
        if (str != null && !str.isEmpty()) {
            String[] chars = str.split(",");
            for (String c : chars) {
                if (!c.trim().isEmpty()) {
                    set.add(c.trim().charAt(0));
                }
            }
        }
        return set;
    }
    
    private String charSetToString(Set<Character> set) {
        if (set == null || set.isEmpty()) {
            return "";
        }
        return set.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }
    
    private int calculateScore(String palabra, Set<Character> letrasIntentadas, boolean palabraCompleta, int intentosRestantes) {
        if (palabraCompleta) {
            return PUNTOS_PALABRA_COMPLETA;
        } else if (intentosRestantes == 0) {
            // Contar letras correctas encontradas
            long letrasCorrectas = letrasIntentadas.stream()
                    .filter(letra -> palabra.indexOf(letra) >= 0)
                    .count();
            return (int) (letrasCorrectas * PUNTOS_POR_LETRA);
        }
        return 0;
    }
    
    private String generateHiddenWord(String palabra, Set<Character> letrasIntentadas) {
        StringBuilder hidden = new StringBuilder();
        for (char c : palabra.toCharArray()) {
            if (letrasIntentadas.contains(c) || c == ' ') {
                hidden.append(c);
            } else {
                hidden.append('_');
            }
        }
        return hidden.toString();
    }
    
    @Transactional
    private void saveGame(Player player, Word word, boolean ganado, int puntaje) {
        // Asegurar que la palabra esté marcada como utilizada
        if (!word.getUtilizada()) {
            word.setUtilizada(true);
            wordRepository.save(word);
        }
        
        Game game = new Game();
        game.setJugador(player);
        game.setPalabra(word);
        game.setResultado(ganado ? "GANADO" : "PERDIDO");
        game.setPuntaje(puntaje);
        game.setFechaPartida(LocalDateTime.now());
        gameRepository.save(game);
    }
    
    public List<GameDTO> getGamesByPlayer(Long playerId) {
        return gameRepository.findByJugadorId(playerId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
    
    public List<GameDTO> getAllGames() {
        return gameRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
    
    private GameDTO toDTO(Game game) {
        GameDTO dto = new GameDTO();
        dto.setId(game.getId());
        dto.setIdJugador(game.getJugador().getId());
        dto.setNombreJugador(game.getJugador().getNombre());
        dto.setResultado(game.getResultado());
        dto.setPuntaje(game.getPuntaje());
        dto.setFechaPartida(game.getFechaPartida());
        dto.setPalabra(game.getPalabra() != null ? game.getPalabra().getPalabra() : null);
        return dto;
    }
}

