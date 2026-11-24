package com.ourstory;

import java.util.ArrayList;
import java.util.List;

/** Complete chess rules: legal moves, check, castling, en passant, promotion, mate and draws. */
public final class ChessRules {
    public static final int EMPTY = 0, PAWN = 1, KNIGHT = 2, BISHOP = 3, ROOK = 4, QUEEN = 5, KING = 6;
    public static final int ONGOING = 0, CHECKMATE = 1, STALEMATE = 2, DRAW = 3;

    /** A move. Squares are rank * 8 + file; rank 0 is White's back rank. */
    public static final class Move {
        public final int from, to, promo;
        public final boolean enPassant, castle;
        Move(int from, int to, int promo, boolean enPassant, boolean castle) {
            this.from = from; this.to = to; this.promo = promo; this.enPassant = enPassant; this.castle = castle;
        }
    }

    // positive = white piece, negative = black piece
    private final int[] board = new int[64];
    private boolean whiteToMove = true;
    private int castling = 15; // 1 = white king side, 2 = white queen side, 4 = black king side, 8 = black queen side
    private int epSquare = -1;
    private int halfMoves = 0;

    public static ChessRules start() {
        ChessRules r = new ChessRules();
        int[] back = {ROOK, KNIGHT, BISHOP, QUEEN, KING, BISHOP, KNIGHT, ROOK};
        for (int f = 0; f < 8; f++) {
            r.board[f] = back[f];
            r.board[8 + f] = PAWN;
            r.board[48 + f] = -PAWN;
            r.board[56 + f] = -back[f];
        }
        return r;
    }

    /** Position from a FEN string (used by tests). */
    public static ChessRules fromFen(String fen) {
        ChessRules r = new ChessRules();
        String[] parts = fen.split(" ");
        String[] rows = parts[0].split("/");
        for (int i = 0; i < 8; i++) {
            int rank = 7 - i, file = 0;
            for (char c : rows[i].toCharArray()) {
                if (Character.isDigit(c)) { file += c - '0'; continue; }
                int t = "pnbrqk".indexOf(Character.toLowerCase(c)) + 1;
                r.board[rank * 8 + file++] = Character.isUpperCase(c) ? t : -t;
            }
        }
        r.whiteToMove = parts[1].equals("w");
        r.castling = 0;
        if (parts[2].contains("K")) r.castling |= 1;
        if (parts[2].contains("Q")) r.castling |= 2;
        if (parts[2].contains("k")) r.castling |= 4;
        if (parts[2].contains("q")) r.castling |= 8;
        r.epSquare = parts[3].equals("-") ? -1 : (parts[3].charAt(1) - '1') * 8 + (parts[3].charAt(0) - 'a');
        return r;
    }

    public ChessRules copy() {
        ChessRules r = new ChessRules();
        System.arraycopy(board, 0, r.board, 0, 64);
        r.whiteToMove = whiteToMove;
        r.castling = castling;
        r.epSquare = epSquare;
        r.halfMoves = halfMoves;
        return r;
    }

    public int at(int sq) { return board[sq]; }
    public boolean whiteToMove() { return whiteToMove; }

    // ------------------------------------------------------------------ attacks

    private static final int[][] KNIGHT_STEPS = {{1, 2}, {2, 1}, {2, -1}, {1, -2}, {-1, -2}, {-2, -1}, {-2, 1}, {-1, 2}};
    private static final int[][] KING_STEPS = {{1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};
    private static final int[][] DIAGONALS = {{1, 1}, {-1, 1}, {-1, -1}, {1, -1}};
    private static final int[][] STRAIGHTS = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};

    /** True if a piece of the given colour attacks the square. */
    public boolean attacked(int sq, boolean byWhite) {
        int f = sq & 7, r = sq >> 3, sign = byWhite ? 1 : -1;
        int pr = r + (byWhite ? -1 : 1);
        for (int df = -1; df <= 1; df += 2) {
            int pf = f + df;
            if (pf >= 0 && pf < 8 && pr >= 0 && pr < 8 && board[pr * 8 + pf] == sign * PAWN) return true;
        }
        for (int[] s : KNIGHT_STEPS) {
            int nf = f + s[0], nr = r + s[1];
            if (nf >= 0 && nf < 8 && nr >= 0 && nr < 8 && board[nr * 8 + nf] == sign * KNIGHT) return true;
        }
        for (int[] s : KING_STEPS) {
            int nf = f + s[0], nr = r + s[1];
            if (nf >= 0 && nf < 8 && nr >= 0 && nr < 8 && board[nr * 8 + nf] == sign * KING) return true;
        }
        for (int[] d : DIAGONALS) {
            int nf = f + d[0], nr = r + d[1];
            while (nf >= 0 && nf < 8 && nr >= 0 && nr < 8) {
                int p = board[nr * 8 + nf];
                if (p != 0) { if (p == sign * BISHOP || p == sign * QUEEN) return true; break; }
                nf += d[0]; nr += d[1];
            }
        }
        for (int[] d : STRAIGHTS) {
            int nf = f + d[0], nr = r + d[1];
            while (nf >= 0 && nf < 8 && nr >= 0 && nr < 8) {
                int p = board[nr * 8 + nf];
                if (p != 0) { if (p == sign * ROOK || p == sign * QUEEN) return true; break; }
                nf += d[0]; nr += d[1];
            }
        }
        return false;
    }

    public int kingSquare(boolean white) {
        for (int i = 0; i < 64; i++) if (board[i] == (white ? KING : -KING)) return i;
        return -1;
    }

    public boolean inCheck(boolean white) {
        int k = kingSquare(white);
        return k >= 0 && attacked(k, !white);
    }

    // ------------------------------------------------------------------ move generation

    private void add(List<Move> out, int from, int to) { out.add(new Move(from, to, 0, false, false)); }

    private List<Move> pseudoMoves() {
        List<Move> out = new ArrayList<>();
        int sign = whiteToMove ? 1 : -1;
        for (int sq = 0; sq < 64; sq++) {
            int p = board[sq];
            if (p == 0 || (p > 0) != whiteToMove) continue;
            int type = Math.abs(p), f = sq & 7, r = sq >> 3;
            switch (type) {
                case PAWN: {
                    int dir = sign, startRank = whiteToMove ? 1 : 6, lastRank = whiteToMove ? 7 : 0;
                    int nr = r + dir;
                    if (nr < 0 || nr > 7) break;
                    if (board[nr * 8 + f] == 0) {
                        pawnMove(out, sq, nr * 8 + f, nr == lastRank);
                        if (r == startRank && board[(r + 2 * dir) * 8 + f] == 0) add(out, sq, (r + 2 * dir) * 8 + f);
                    }
                    for (int df = -1; df <= 1; df += 2) {
                        int nf = f + df;
                        if (nf < 0 || nf > 7) continue;
                        int to = nr * 8 + nf;
                        if (board[to] != 0 && (board[to] > 0) != whiteToMove) pawnMove(out, sq, to, nr == lastRank);
                        else if (to == epSquare) out.add(new Move(sq, to, 0, true, false));
                    }
                    break;
                }
                case KNIGHT:
                    for (int[] s : KNIGHT_STEPS) step(out, sq, f + s[0], r + s[1]);
                    break;
                case KING:
                    for (int[] s : KING_STEPS) step(out, sq, f + s[0], r + s[1]);
                    castleMoves(out, sq);
                    break;
                default:
                    if (type == BISHOP || type == QUEEN) for (int[] d : DIAGONALS) slide(out, sq, d);
                    if (type == ROOK || type == QUEEN) for (int[] d : STRAIGHTS) slide(out, sq, d);
            }
        }
        return out;
    }

    private void pawnMove(List<Move> out, int from, int to, boolean promotes) {
        if (!promotes) { add(out, from, to); return; }
        for (int promo : new int[]{QUEEN, ROOK, BISHOP, KNIGHT}) out.add(new Move(from, to, promo, false, false));
    }

    private void step(List<Move> out, int from, int nf, int nr) {
        if (nf < 0 || nf > 7 || nr < 0 || nr > 7) return;
        int p = board[nr * 8 + nf];
        if (p == 0 || (p > 0) != whiteToMove) add(out, from, nr * 8 + nf);
    }

    private void slide(List<Move> out, int from, int[] d) {
        int nf = (from & 7) + d[0], nr = (from >> 3) + d[1];
        while (nf >= 0 && nf < 8 && nr >= 0 && nr < 8) {
            int p = board[nr * 8 + nf];
            if (p == 0) add(out, from, nr * 8 + nf);
            else { if ((p > 0) != whiteToMove) add(out, from, nr * 8 + nf); break; }
            nf += d[0]; nr += d[1];
        }
    }

    private void castleMoves(List<Move> out, int sq) {
        int home = whiteToMove ? 4 : 60;
        if (sq != home || inCheck(whiteToMove)) return;
        int kingSide = whiteToMove ? 1 : 4, queenSide = whiteToMove ? 2 : 8, rook = whiteToMove ? ROOK : -ROOK;
        if ((castling & kingSide) != 0 && board[home + 3] == rook && board[home + 1] == 0 && board[home + 2] == 0
                && !attacked(home + 1, !whiteToMove) && !attacked(home + 2, !whiteToMove))
            out.add(new Move(sq, home + 2, 0, false, true));
        if ((castling & queenSide) != 0 && board[home - 4] == rook && board[home - 1] == 0 && board[home - 2] == 0
                && board[home - 3] == 0 && !attacked(home - 1, !whiteToMove) && !attacked(home - 2, !whiteToMove))
            out.add(new Move(sq, home - 2, 0, false, true));
    }

    /** All legal moves for the side to move. */
    public List<Move> legal() {
        List<Move> out = new ArrayList<>();
        for (Move m : pseudoMoves()) {
            ChessRules next = after(m);
            if (!next.inCheck(whiteToMove)) out.add(m);
        }
        return out;
    }

    public List<Move> legalFrom(int sq) {
        List<Move> out = new ArrayList<>();
        for (Move m : legal()) if (m.from == sq) out.add(m);
        return out;
    }

    /** The legal move from -> to (promotion always becomes a queen), or null. */
    public Move find(int from, int to) {
        Move found = null;
        for (Move m : legal()) {
            if (m.from == from && m.to == to && (m.promo == 0 || m.promo == QUEEN)) found = m;
        }
        return found;
    }

    /** The position after playing m (the original is not changed). */
    public ChessRules after(Move m) {
        ChessRules r = copy();
        int piece = r.board[m.from];
        int type = Math.abs(piece);
        boolean capture = r.board[m.to] != 0 || m.enPassant;
        r.board[m.from] = 0;
        if (m.enPassant) r.board[m.to + (whiteToMove ? -8 : 8)] = 0;
        r.board[m.to] = m.promo != 0 ? (whiteToMove ? m.promo : -m.promo) : piece;
        if (m.castle) {
            int home = m.from;
            if (m.to > m.from) { r.board[home + 1] = r.board[home + 3]; r.board[home + 3] = 0; }
            else { r.board[home - 1] = r.board[home - 4]; r.board[home - 4] = 0; }
        }
        if (type == KING) r.castling &= whiteToMove ? ~3 : ~12;
        if (m.from == 0 || m.to == 0) r.castling &= ~2;
        if (m.from == 7 || m.to == 7) r.castling &= ~1;
        if (m.from == 56 || m.to == 56) r.castling &= ~8;
        if (m.from == 63 || m.to == 63) r.castling &= ~4;
        r.epSquare = (type == PAWN && Math.abs(m.to - m.from) == 16) ? (m.from + m.to) / 2 : -1;
        r.halfMoves = (type == PAWN || capture) ? 0 : halfMoves + 1;
        r.whiteToMove = !whiteToMove;
        return r;
    }

    private boolean insufficientMaterial() {
        int minors = 0;
        for (int i = 0; i < 64; i++) {
            int t = Math.abs(board[i]);
            if (t == PAWN || t == ROOK || t == QUEEN) return false;
            if (t == KNIGHT || t == BISHOP) minors++;
        }
        return minors <= 1;
    }

    /** ONGOING, CHECKMATE (side to move has lost), STALEMATE or DRAW. */
    public int result() {
        if (legal().isEmpty()) return inCheck(whiteToMove) ? CHECKMATE : STALEMATE;
        if (insufficientMaterial() || halfMoves >= 100) return DRAW;
        return ONGOING;
    }
}
